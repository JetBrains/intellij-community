// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net

import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.net.ProxyConfiguration.Companion.autodetect
import org.jetbrains.annotations.ApiStatus

/**
 * [ProxySettings] holds user-specified proxy settings (Settings | Appearance & Behavior | System Settings | HTTP Proxy).
 *
 * @see JdkProxyProvider
 * @see ProxyAuthentication
 */
interface ProxySettings {
  companion object {
    @JvmStatic
    fun getInstance(): ProxySettings = ApplicationManager.getApplication().getService(ProxySettings::class.java)

    @JvmStatic
    val defaultProxyConfiguration: ProxyConfiguration get() = autodetect
  }

  fun getProxyConfiguration(): ProxyConfiguration

  @ApiStatus.Experimental // maybe make it internal if we decide that plugins must not edit user settings
  fun setProxyConfiguration(proxyConfiguration: ProxyConfiguration)
}

fun interface ProxyConfigurationProvider {
  fun getProxyConfiguration(): ProxyConfiguration
}

@Deprecated("Pointless; use `ProxySettings.getProxyConfiguration` directly", level = DeprecationLevel.ERROR)
fun ProxySettings.asConfigurationProvider(): ProxyConfigurationProvider = ProxyConfigurationProvider(this::getProxyConfiguration)
