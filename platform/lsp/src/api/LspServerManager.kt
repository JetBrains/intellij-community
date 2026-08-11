// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.api

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@Deprecated("Use LspClientManager", ReplaceWith("LspClientManager", "com.intellij.platform.lsp.api.LspClientManager"))
@Suppress("DEPRECATION")
interface LspServerManager : LspClientManager {
  @Deprecated("Use getClients", ReplaceWith("getClients(providerClass)"))
  fun getServersForProvider(providerClass: Class<out LspServerSupportProvider>): Collection<LspServer>

  @Deprecated("Use startClientsIfNeeded", ReplaceWith("startClientsIfNeeded(providerClass)"))
  fun startServersIfNeeded(providerClass: Class<out LspServerSupportProvider>)

  @Deprecated("Use ensureClientStarted", ReplaceWith("ensureClientStarted(providerClass, descriptor)"))
  fun ensureServerStarted(providerClass: Class<out LspServerSupportProvider>, descriptor: LspServerDescriptor)

  @Deprecated("Use stopClients", ReplaceWith("stopClients(providerClass)"))
  fun stopServers(providerClass: Class<out LspServerSupportProvider>)

  @Deprecated("Use stopAndRestartClientsIfNeeded", ReplaceWith("stopAndRestartClientsIfNeeded(providerClass)"))
  fun stopAndRestartIfNeeded(providerClass: Class<out LspServerSupportProvider>)

  @ApiStatus.Internal
  @Deprecated("Use addListener", ReplaceWith("addListener(listener, parentDisposable, sendEventsForExistingServers)"))
  fun addLspServerManagerListener(
    listener: LspServerManagerListener,
    parentDisposable: Disposable,
    sendEventsForExistingServers: Boolean = false,
  ): Unit = addListener(listener, parentDisposable, sendEventsForExistingServers)

  companion object {
    /**
     * The project service is registered only against [LspClientManager], so a direct service-container lookup such as
     * `project.service<LspServerManager>()` returns `null`. This entry point keeps working; prefer [LspClientManager.getInstance].
     */
    @JvmStatic
    @Deprecated(
      "Use LspClientManager.getInstance",
      ReplaceWith("LspClientManager.getInstance(project)", "com.intellij.platform.lsp.api.LspClientManager"),
    )
    fun getInstance(project: Project): LspServerManager =
      @Suppress("DEPRECATION") (LspClientManager.getInstance(project) as LspServerManager)
  }
}

@Deprecated("Use getClients", ReplaceWith("getClients<Provider>()", "com.intellij.platform.lsp.api.getClients"))
@Suppress("DEPRECATION")
inline fun <reified Provider : LspServerSupportProvider> LspServerManager.getServersForProvider(): Collection<LspServer> =
  getServersForProvider(Provider::class.java)

@Deprecated("Use startClientsIfNeeded", ReplaceWith("startClientsIfNeeded<Provider>()", "com.intellij.platform.lsp.api.startClientsIfNeeded"))
@Suppress("DEPRECATION")
inline fun <reified Provider : LspServerSupportProvider> LspServerManager.startServersIfNeeded(): Unit =
  startServersIfNeeded(Provider::class.java)

@Deprecated("Use ensureClientStarted", ReplaceWith("ensureClientStarted<Provider>(descriptor)", "com.intellij.platform.lsp.api.ensureClientStarted"))
@Suppress("DEPRECATION")
inline fun <reified Provider : LspServerSupportProvider> LspServerManager.ensureServerStarted(descriptor: LspServerDescriptor): Unit =
  ensureServerStarted(Provider::class.java, descriptor)

@Deprecated("Use stopClients", ReplaceWith("stopClients<Provider>()", "com.intellij.platform.lsp.api.stopClients"))
@Suppress("DEPRECATION")
inline fun <reified Provider : LspServerSupportProvider> LspServerManager.stopServers(): Unit =
  stopServers(Provider::class.java)

@Deprecated(
  "Use stopAndRestartClientsIfNeeded",
  ReplaceWith("stopAndRestartClientsIfNeeded<Provider>()", "com.intellij.platform.lsp.api.stopAndRestartClientsIfNeeded"),
)
@Suppress("DEPRECATION")
inline fun <reified Provider : LspServerSupportProvider> LspServerManager.stopAndRestartIfNeeded(): Unit =
  stopAndRestartIfNeeded(Provider::class.java)
