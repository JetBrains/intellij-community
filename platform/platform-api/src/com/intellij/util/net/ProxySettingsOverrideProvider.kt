// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginDescriptor
import org.jetbrains.annotations.ApiStatus

/**
 * Only the first extension with [shouldUserSettingsBeOverriden]=`true` has effect.
 *
 * If the proxy configuration requires authentication and the credentials are known, the credential store can be populated via
 * [ProxyAuthentication.instance].
 */
interface ProxySettingsOverrideProvider {
  companion object {
    @JvmField
    @ApiStatus.Internal
    val EP_NAME: ExtensionPointName<ProxySettingsOverrideProvider> = ExtensionPointName("com.intellij.proxySettingsOverrideProvider")

    @ApiStatus.Internal
    fun getPluginDescriptorForProvider(overrideProvider: ProxySettingsOverrideProvider): PluginDescriptor? {
      var result: PluginDescriptor? = null
      EP_NAME.processWithPluginDescriptor { provider, plugin ->
        if (overrideProvider === provider) {
          result = plugin
        }
      }
      return result
    }

    @JvmStatic
    fun areProxySettingsOverridden(): Boolean {
      val settings = ProxySettings.getInstance() as? OverrideCapableProxySettings ?: return false
      return settings.isOverrideEnabled && settings.overrideProvider != null
    }
  }

  /**
   * Should return true if the provider wants to override user's proxy settings. Expected to be immutable.
   */
  val shouldUserSettingsBeOverriden: Boolean

  /**
   * [ProxyConfigurationProvider] which provides the proxy configuration to be used instead of user settings.
   */
  val proxyConfigurationProvider: ProxyConfigurationProvider
}