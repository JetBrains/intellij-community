// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.auth.ui.login

import com.intellij.collaboration.auth.ui.login.LoginModel.LoginState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

abstract class LoginPanelModelBase : TokenLoginPanelModel {
  final override var serverUri: String = ""
  final override var token: String = ""

  private val _loginState = MutableStateFlow<LoginState>(LoginState.Disconnected)

  override val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

  final override suspend fun login() {
    _loginState.update { LoginState.Connecting }
    try {
      val username = checkToken()
      _loginState.update { LoginState.Connected(username) }
    }
    catch (e: CancellationException) {
      _loginState.update { LoginState.Disconnected }
    }
    catch (e: Throwable) {
      _loginState.update { LoginState.Failed(e) }
    }
  }

  abstract suspend fun checkToken(): String
}