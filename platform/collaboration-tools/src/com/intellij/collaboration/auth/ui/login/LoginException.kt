// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.auth.ui.login

import com.intellij.openapi.util.NlsSafe

sealed class LoginException : Exception() {
  class UnsupportedServerVersion(val earliestSupportedVersion: String) : LoginException()
  class InvalidTokenOrUnsupportedServerVersion(val earliestSupportedVersion: String) : LoginException()
  class AccountAlreadyExists(val username: @NlsSafe String) : LoginException()
  class AccountUsernameMismatch(val requiredUsername: @NlsSafe String, val username: @NlsSafe String) : LoginException()
}