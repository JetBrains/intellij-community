// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.SERVICE_NAME_PREFIX
import com.intellij.credentialStore.isFulfilled
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.platform.settings.CacheStateTag
import com.intellij.platform.settings.SettingsController
import com.intellij.platform.settings.objectSerializer
import com.intellij.platform.settings.settingDescriptorFactory
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlinx.serialization.Serializable

@Service
internal class ErrorReportConfigurable {
  companion object {
    private const val SERVICE_NAME = "$SERVICE_NAME_PREFIX — JetBrains Account"

    fun getInstance(): ErrorReportConfigurable = service<ErrorReportConfigurable>()

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
  }

  // this will be injected by ComponentManager (a client will request it from a coroutine scope as a service)
  private val settingDescriptorFactory = settingDescriptorFactory(PluginManagerCore.CORE_ID)

  private val settingDescriptor = settingDescriptorFactory.settingDescriptor(
    key = "ideErrorReporter.developerList",
    serializer = settingDescriptorFactory.objectSerializer<DeveloperList>(),
  ) {
    tags = listOf(CacheStateTag)
  }

  suspend fun getDeveloperList(): DeveloperList {
    //todo get service from injected coroutine scope
    return serviceAsync<SettingsController>().getItem(settingDescriptor) ?: DeveloperList()
  }

  suspend fun setDeveloperList(updatedDevelopers: DeveloperList) {
    serviceAsync<SettingsController>().setItem(settingDescriptor, updatedDevelopers)
  }
}

private data class CredentialsState(val userName: String?, val isFulfilled: Boolean)

private fun credentialsState(credentials: Credentials?) = CredentialsState(credentials?.userName ?: "", credentials.isFulfilled())

// 24 hours
private const val UPDATE_INTERVAL = 24L * 60 * 60 * 1000

@Serializable
internal data class DeveloperList(@JvmField val developers: List<Developer> = emptyList(), @JvmField val timestamp: Long = 0) {
  fun isUpToDateAt(): Boolean = timestamp != 0L && (System.currentTimeMillis() - timestamp) < UPDATE_INTERVAL
}

@Serializable
internal data class Developer(@JvmField val id: Int, @JvmField val displayText: String) {
  companion object {
    val NULL: Developer = Developer(id = -1, displayText = "<none>")
  }
}