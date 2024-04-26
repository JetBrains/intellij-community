// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.auth.ui.login

import com.intellij.openapi.util.NlsSafe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.transformLatest

/**
 * Model for login process
 *
 * @property loginState represents the current state of the log in process
 */
interface LoginModel {

  val loginState: StateFlow<LoginState>

  /**
   * Performs a login and updates [loginState]
   */
  suspend fun login()

  sealed class LoginState {
    data object Disconnected : LoginState()
    data object Connecting : LoginState()
    class Failed(val error: Throwable) : LoginState()
    class Connected(val username: @NlsSafe String) : LoginState()
  }
}

@OptIn(ExperimentalCoroutinesApi::class)
val LoginModel.errorFlow: Flow<Throwable?>
  get() = loginState.transformLatest { loginState ->
    when (loginState) {
      LoginModel.LoginState.Connecting -> emit(null)
      is LoginModel.LoginState.Failed -> emit(loginState.error)
      else -> {}
    }
  }