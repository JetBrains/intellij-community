// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class OverrideCapableProxySettings : ProxySettings, ProxyConfigurationProvider {
  abstract val originalProxySettings: ProxySettings

  abstract var isOverrideEnabled: Boolean

  /**
   * contract: if not null, then [ProxySettingsOverrideProvider.shouldUserSettingsBeOverriden] == true
   */
  abstract val overrideProvider: ProxySettingsOverrideProvider?

  final override fun getProxyConfiguration(): ProxyConfiguration {
    if (isOverrideEnabled) {
      val overrideConf = overrideProvider?.proxyConfigurationProvider?.getProxyConfiguration()
      if (overrideConf != null) {
        return overrideConf
      }
    }
    return originalProxySettings.getProxyConfiguration()
  }

  final override fun setProxyConfiguration(proxyConfiguration: ProxyConfiguration) {
    if (isOverrideEnabled && overrideProvider != null) return
    originalProxySettings.setProxyConfiguration(proxyConfiguration)
  }
}