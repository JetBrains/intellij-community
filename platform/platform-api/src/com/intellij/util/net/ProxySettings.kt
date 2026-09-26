// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net

import com.intellij.openapi.application.ApplicationManager
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
    fun getInstance(): ProxySettings = ApplicationManager.getApplication().getService(ProxySettings::class.java) ?: DEFAULT

    @JvmStatic
    val defaultProxyConfiguration: ProxyConfiguration get() = ProxyConfiguration.autodetect

    private val DEFAULT = object : ProxySettings {
      override fun getProxyConfiguration(): ProxyConfiguration = ProxyConfiguration.direct
      override fun setProxyConfiguration(proxyConfiguration: ProxyConfiguration): Unit = throw UnsupportedOperationException()
    }
  }

  fun getProxyConfiguration(): ProxyConfiguration

  @ApiStatus.Internal
  fun setProxyConfiguration(proxyConfiguration: ProxyConfiguration)
}

fun interface ProxyConfigurationProvider {
  fun getProxyConfiguration(): ProxyConfiguration
}

@Suppress("DeprecatedCallableAddReplaceWith")
@Deprecated("Pointless; use `ProxySettings.getProxyConfiguration` directly", level = DeprecationLevel.ERROR)
fun ProxySettings.asConfigurationProvider(): ProxyConfigurationProvider = ProxyConfigurationProvider(this::getProxyConfiguration)
