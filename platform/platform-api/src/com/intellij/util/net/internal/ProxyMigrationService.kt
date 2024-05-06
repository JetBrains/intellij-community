// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net.internal

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ConfigurableUi
import com.intellij.util.net.DisabledProxyAuthPromptsManager
import com.intellij.util.net.ProxyCredentialStore
import com.intellij.util.net.ProxySettings
import org.jetbrains.annotations.ApiStatus

/**
 * This is a **temporary** utility for switching the default in HttpConfigurable and for migrating stuff out of platform-api module.
 * Do not use. It will be removed once HttpConfigurable is deprecated and migration to a new API for proxy settings is made.
 */
@ApiStatus.Internal
interface ProxyMigrationService {
  companion object {
    @JvmStatic
    fun getInstance(): ProxyMigrationService = ApplicationManager.getApplication().getService(ProxyMigrationService::class.java)
  }

  fun isNewUser(): Boolean

  fun createProxySettingsUi(
    proxySettings: ProxySettings,
    credentialStore: ProxyCredentialStore,
    disabledProxyAuthPromptsManager: DisabledProxyAuthPromptsManager
  ): ConfigurableUi<ProxySettings>
}