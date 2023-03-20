// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.auth.ui.login

import com.intellij.openapi.util.NlsSafe
import kotlinx.coroutines.flow.StateFlow

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
    object Disconnected : LoginState()
    object Connecting : LoginState()
    class Failed(val error: Throwable) : LoginState()
    class Connected(val username: @NlsSafe String) : LoginState()
  }
}