// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net

import com.intellij.ide.ApplicationInitializedListener
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.proxy.CommonProxyCompatibility
import java.net.Authenticator
import java.net.ProxySelector

sealed interface JdkProxyProvider {
  val proxySelector: ProxySelector
  val authenticator: Authenticator

  companion object {
    /**
     * [JdkProxyProvider.getInstance] acts as the main proxy provider in the application and is configured by user [ProxySettings].
     * It registers its [proxySelector] and [authenticator] as the default ([ProxySelector.setDefault] and [Authenticator.setDefault]).
     * This setting must not be changed. To ensure this contract, use [JdkProxyProvider.ensureDefault].
     *
     * If customization of the default [JdkProxyProvider] is required, prefer to implement your own [ProxySelector] and [Authenticator]
     * for your subsystem, delegating the base functionality to [ProxyAuthentication.getInstance] or [JdkProxyProvider.getInstance].
     * If this is not possible or too hard to implement, use [JdkProxyCustomizer].
     *
     * @see [IdeProxySelector]
     * @see [ProxyAuthentication]
     */
    @JvmStatic
    fun getInstance(): JdkProxyProvider = CustomizedPlatformJdkProxyProvider

    /**
     * This utility ensures that [ProxySelector] and [Authenticator] from [JdkProxyProvider.getInstance] are used by default by the
     * java network stack.
     */
    @JvmStatic
    fun ensureDefault(): Unit = ensureDefaultProxyProviderImpl()
  }
}

private class OverrideDefaultJdkProxy : ApplicationInitializedListener {
  override suspend fun execute() {
    val jdkProxyProvider = JdkProxyProvider.getInstance()
    val jdkProxyCustomizer = JdkProxyCustomizer.getInstance()
    CommonProxyCompatibility.register(
      proxySelector = jdkProxyProvider.proxySelector,
      authenticator = jdkProxyProvider.authenticator,
      registerCustomProxySelector = jdkProxyCustomizer::customizeProxySelector,
      registerCustomAuthenticator = jdkProxyCustomizer::customizeAuthenticator
    )
    JdkProxyProvider.ensureDefault()
  }
}

@Synchronized
private fun ensureDefaultProxyProviderImpl() {
  val provider = JdkProxyProvider.getInstance()
  val proxySelector = provider.proxySelector
  val authenticator = provider.authenticator
  if (!javaProxyInstallationFlag) {
    ProxySelector.setDefault(proxySelector)
    Authenticator.setDefault(authenticator)
    javaProxyInstallationFlag = true
    return
  }
  val defaultProxySelector = ProxySelector.getDefault()
  if (defaultProxySelector !== proxySelector) {
    logger<ProxySelector>().error("""
      ProxySelector.setDefault() was changed to [$defaultProxySelector] - other than [$proxySelector].
      This will make some ${ApplicationNamesInfo.getInstance().productName} network calls fail.
      Instead, ProxyService.instance.proxySelector should be the default proxy selector.
      """.trimIndent()
    )
    ProxySelector.setDefault(proxySelector)
  }
  val defaultAuthenticator = Authenticator.getDefault()
  if (defaultAuthenticator !== authenticator) {
    logger<ProxySelector>().error("""
      Authenticator.setDefault() was changed to [$defaultAuthenticator] - other than [$authenticator].
      This may make some ${ApplicationNamesInfo.getInstance().productName} network calls fail.
      Instead, ProxyService.instance.authenticator should be used as a default proxy authenticator.
      """.trimIndent()
    )
    Authenticator.setDefault(authenticator)
  }
}

private var javaProxyInstallationFlag: Boolean = false