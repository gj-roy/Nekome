package com.chesire.malime.view.login.kitsu

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import android.databinding.ObservableBoolean
import com.chesire.malime.R
import com.chesire.malime.core.flags.SupportedService
import com.chesire.malime.kitsu.api.KitsuAuthorizer
import com.chesire.malime.kitsu.api.KitsuManagerFactory
import com.chesire.malime.util.IOScheduler
import com.chesire.malime.util.SharedPref
import com.chesire.malime.util.UIScheduler
import com.chesire.malime.view.login.LoginModel
import com.chesire.malime.view.login.LoginStatus
import io.reactivex.Scheduler
import io.reactivex.disposables.CompositeDisposable
import javax.inject.Inject

class KitsuLoginViewModel @Inject constructor(
    private val sharedPref: SharedPref,
    private val kitsuManagerFactory: KitsuManagerFactory,
    private val authorizer: KitsuAuthorizer
) : ViewModel() {
    private val disposables = CompositeDisposable()
    val loginResponse = MutableLiveData<LoginStatus>()
    val errorResponse = MutableLiveData<Int>()
    val attemptingLogin = ObservableBoolean()
    val loginModel = LoginModel()

    @Inject
    @field:IOScheduler
    lateinit var subscribeScheduler: Scheduler

    @Inject
    @field:UIScheduler
    lateinit var observeScheduler: Scheduler

    fun executeLogin() {
        if (!isValid(loginModel.userName, loginModel.password)) {
            return
        }

        val kitsuManager = kitsuManagerFactory.get(authorizer)
        disposables.add(kitsuManager.login(loginModel.userName, loginModel.password)
            .flatMap {
                authorizer.storeAuthDetails(it)
                return@flatMap kitsuManager.getUserId()
            }
            .subscribeOn(subscribeScheduler)
            .observeOn(observeScheduler)
            .doOnSubscribe {
                attemptingLogin.set(true)
                loginResponse.value = LoginStatus.PROCESSING
            }
            .doFinally {
                attemptingLogin.set(false)
                loginResponse.value = LoginStatus.FINISHED
            }
            .doOnError {
                errorResponse.value = R.string.login_failure
                loginResponse.value = LoginStatus.ERROR
                authorizer.clear()
            }
            .doOnSuccess {
                loginResponse.value = LoginStatus.SUCCESS

                sharedPref.putPrimaryService(SupportedService.Kitsu)
                authorizer.storeUser(it)
            }
            .subscribe()
        )
    }

    private fun isValid(username: String, password: String): Boolean {
        return when {
            username.isBlank() -> {
                errorResponse.value = R.string.login_failure_email
                false
            }
            password.isBlank() -> {
                errorResponse.value = R.string.login_failure_password
                false
            }
            else -> true
        }
    }

    override fun onCleared() {
        disposables.clear()
        super.onCleared()
    }
}