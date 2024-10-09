// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.SERVICE_NAME_PREFIX
import com.intellij.credentialStore.isFulfilled
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.components.Service
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread

@Service
internal class ErrorReportConfigurable {
  companion object {
    private const val SERVICE_NAME = "${SERVICE_NAME_PREFIX} — JetBrains Account"

    @RequiresBackgroundThread
    fun getCredentials(): Credentials? {
      ThreadingAssertions.assertBackgroundThread()
      return PasswordSafe.instance.get(CredentialAttributes(SERVICE_NAME))
    }

    fun saveCredentials(userName: String?, password: CharArray?) {
      val credentials = Credentials(userName, password)
      PasswordSafe.instance.set(CredentialAttributes(SERVICE_NAME, userName), credentials)
      lastCredentialsState = credentialsState(credentials)
    }

    val userName: String?
      @RequiresBackgroundThread
      get() = getCredentialsState().userName

    val credentialsFulfilled: Boolean
      @RequiresBackgroundThread
      get() = getCredentialsState().isFulfilled

    private var lastCredentialsState: CredentialsState? = null

    @RequiresBackgroundThread
    private fun getCredentialsState(): CredentialsState = lastCredentialsState ?: credentialsState(getCredentials())

    private fun credentialsState(credentials: Credentials?) = CredentialsState(credentials?.userName ?: "", credentials.isFulfilled())
  }
}

private data class CredentialsState(val userName: String?, val isFulfilled: Boolean)
