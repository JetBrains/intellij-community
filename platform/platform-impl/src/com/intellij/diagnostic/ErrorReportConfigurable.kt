// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.SERVICE_NAME_PREFIX
import com.intellij.credentialStore.isFulfilled
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.components.*
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlinx.serialization.Serializable

@Service
@State(name = "ErrorReportConfigurable", storages = [Storage(StoragePathMacros.CACHE_FILE)])
internal class ErrorReportConfigurable : PersistentStateComponent<DeveloperList>, SimpleModificationTracker() {
  companion object {
    @JvmStatic
    private val SERVICE_NAME = "$SERVICE_NAME_PREFIX — JetBrains Account"

    @JvmStatic
    fun getInstance(): ErrorReportConfigurable = service<ErrorReportConfigurable>()

    @RequiresBackgroundThread
    @JvmStatic
    fun getCredentials(): Credentials? {
      ThreadingAssertions.assertBackgroundThread();
      return PasswordSafe.instance.get(CredentialAttributes(SERVICE_NAME))
    }

    @JvmStatic
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
  }

  var developerList: DeveloperList = DeveloperList()
    set(value) {
      field = value
      incModificationCount()
    }

  override fun getState(): DeveloperList = developerList

  override fun loadState(value: DeveloperList) {
    developerList = value
  }
}

private data class CredentialsState(val userName: String?, val isFulfilled: Boolean)

private fun credentialsState(credentials: Credentials?) = CredentialsState(credentials?.userName ?: "", credentials.isFulfilled())

// 24 hours
private const val UPDATE_INTERVAL = 24L * 60 * 60 * 1000

@Serializable
internal data class DeveloperList(val developers: List<Developer> = emptyList(), val timestamp: Long = 0) {
  fun isUpToDateAt(): Boolean = timestamp != 0L && (System.currentTimeMillis() - timestamp) < UPDATE_INTERVAL
}

@Serializable
internal data class Developer(val id: Int, val displayText: String) {
  companion object {
    val NULL: Developer = Developer(-1, "<none>")
  }
}