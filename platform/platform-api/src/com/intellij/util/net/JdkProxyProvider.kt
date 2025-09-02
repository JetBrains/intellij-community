// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net

import com.intellij.ide.ApplicationInitializedListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.util.net.AppShutdownProxySelector.AppShutdownProxyMode.*
import com.intellij.util.proxy.CommonProxyCompatibility
import java.io.IOException
import java.net.*
import java.util.concurrent.CancellationException

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

    // ideally should be the last thing before the app is set to null, but this should suffice too
    Disposer.register(ApplicationManager.getApplication(), Disposable {
      if (ApplicationManager.getApplication().isUnitTestMode) {
        return@Disposable
      }
      ProxySelector.setDefault(AppShutdownProxySelector())
      Authenticator.setDefault(null)
    })
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

/**
 * If default proxy selector was replaced with [AppShutdownProxySelector], the app is in the progress of shutting down, or is already shut down.
 * Network accesses that are semantically bound to the application lifetime must not outlive it.
 */
private class AppShutdownProxySelector : ProxySelector() {
  @Suppress("EnumEntryName")
  private enum class AppShutdownProxyMode { no_proxy, cancel, error }

  override fun select(uri: URI?): List<Proxy> {
    val property = System.getProperty("ide.proxy.app.shutdown.mode", no_proxy.name) // TODO try to switch to `cancel` sometime
    try { // logger might not work at this point, best-effort
      logger<AppShutdownProxySelector>().warn("network access during/after app shutdown", Throwable())
    } catch (_: Throwable) {}
    return when (property) {
      cancel.name -> throw CancellationException("proxy selection cancelled during/after app shutdown")
      error.name -> throw RuntimeException("proxy selection during/after app shutdown")
      else -> NO_PROXY_LIST
    }
  }

  override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {}
}