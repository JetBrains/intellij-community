// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.api

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

/**
 * Tracks started LSP clients, allows starting, restarting, and stopping LSP clients.
 *
 * Plugins that want to start LSP clients should implement [LspIntegrationProvider].
 *
 * See [LspIntegrationProvider.fileOpened] function documentation for information about starting an LSP client.
 */
interface LspClientManager {
  companion object {
    /**
     * Returns an `LspClientManager` instance for the given project. The passed `project` parameter must not be the 'default project'
     * (the fake one used to manage settings for new projects). So, some callers may need to check [Project.isDefault] before calling
     * `LspClientManager.getInstance(project)`.
     */
    @JvmStatic
    fun getInstance(project: Project): LspClientManager = project.service()
  }

  fun getClients(providerClass: Class<out LspIntegrationProvider>): Collection<LspClient>

  /**
   * This function is designed for cases like "a user has enabled some framework support in Settings." It notifies the `providerClass`
   * about the files that are open in the editor by scheduling the [LspIntegrationProvider.fileOpened] function calls. So, if the
   * [fileOpened][LspIntegrationProvider.fileOpened] function is implemented according to its documentation, this function guarantees
   * that all [LSP servers][LspClient] needed for the currently open files will get started.
   *
   * If one or more [LSP servers][LspClient] are already running, and all the files that are open in the editor are within the roots
   * of the running servers, then this function doesn't do anything.
   *
   * This function may be called from any thread.
   *
   * @see [LspIntegrationProvider.fileOpened]
   */
  fun startClientsIfNeeded(providerClass: Class<out LspIntegrationProvider>)

  /**
   * This function starts an LSP server even if no files are currently open in the editor,
   * so consider using [startClientsIfNeeded] instead.
   *
   * If an [LSP server][LspClient] is already running, and it has the same roots as the passed [descriptor],
   * then this function doesn't do anything.
   *
   * This function may be called from any thread.
   */
  fun ensureClientStarted(providerClass: Class<out LspIntegrationProvider>, descriptor: LspClientDescriptor)

  /**
   * This function is designed for cases like "a user has disabled some framework support in Settings."
   * It stops all running LSP servers associated with the `providerClass`.
   */
  fun stopClients(providerClass: Class<out LspIntegrationProvider>)

  /**
   * Just a shorthand for [stopClients] followed by [startClientsIfNeeded]. Typically, a plugin calls this function when, for example, a
   * user has changed some framework-specific settings, and therefore, an LSP server needs to be restarted with some other parameters.
   */
  fun stopAndRestartClientsIfNeeded(providerClass: Class<out LspIntegrationProvider>)

  @ApiStatus.Internal
  @TestOnly
  fun addLsp4jServerWrapper(wrapper: Lsp4jServerWrapper, parentDisposable: Disposable)

  @ApiStatus.Internal
  fun addListener(listener: LspClientManagerListener, parentDisposable: Disposable, sendEventsForExistingClients: Boolean = false)
}

inline fun <reified Provider : LspIntegrationProvider> LspClientManager.getClients(): Collection<LspClient> =
  getClients(Provider::class.java)

inline fun <reified Provider : LspIntegrationProvider> LspClientManager.startClientsIfNeeded(): Unit =
  startClientsIfNeeded(Provider::class.java)

inline fun <reified Provider : LspIntegrationProvider> LspClientManager.ensureClientStarted(descriptor: LspClientDescriptor): Unit =
  ensureClientStarted(Provider::class.java, descriptor)

inline fun <reified Provider : LspIntegrationProvider> LspClientManager.stopClients(): Unit =
  stopClients(Provider::class.java)

inline fun <reified Provider : LspIntegrationProvider> LspClientManager.stopAndRestartClientsIfNeeded(): Unit =
  stopAndRestartClientsIfNeeded(Provider::class.java)
