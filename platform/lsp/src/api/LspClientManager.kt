// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.api

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

/**
 * Tracks started LSP servers, allows starting, restarting, and stopping LSP servers.
 *
 * Plugins that want to start LSP servers should implement [LspClientProvider].
 *
 * See [LspClientProvider.fileOpened] function documentation for information about starting an LSP server.
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

  fun getClientsForProvider(providerClass: Class<out LspClientProvider>): Collection<LspClient>

  /**
   * This function is designed for cases like "a user has enabled some framework support in Settings." It notifies the `providerClass`
   * about the files that are open in the editor by scheduling the [LspClientProvider.fileOpened] function calls. So, if the
   * [fileOpened][LspClientProvider.fileOpened] function is implemented according to its documentation, this function guarantees
   * that all [LSP servers][LspClient] needed for the currently open files will get started.
   *
   * If one or more [LSP servers][LspClient] are already running, and all the files that are open in the editor are within the roots
   * of the running servers, then this function doesn't do anything.
   *
   * This function may be called from any thread.
   *
   * @see [LspClientProvider.fileOpened]
   */
  fun startClientsIfNeeded(providerClass: Class<out LspClientProvider>)

  /**
   * This function starts an LSP server even if no files are currently open in the editor,
   * so consider using [startClientsIfNeeded] instead.
   *
   * If an [LSP server][LspClient] is already running, and it has the same roots as the passed [descriptor],
   * then this function doesn't do anything.
   *
   * This function may be called from any thread.
   */
  fun ensureClientStarted(providerClass: Class<out LspClientProvider>, descriptor: LspClientDescriptor)

  /**
   * This function is designed for cases like "a user has disabled some framework support in Settings."
   * It stops all running LSP servers associated with the `providerClass`.
   */
  fun stopClients(providerClass: Class<out LspClientProvider>)

  /**
   * Just a shorthand for [stopClients] followed by [startClientsIfNeeded]. Typically, a plugin calls this function when, for example, a
   * user has changed some framework-specific settings, and therefore, an LSP server needs to be restarted with some other parameters.
   */
  fun stopAndRestartClientsIfNeeded(providerClass: Class<out LspClientProvider>)

  @ApiStatus.Internal
  @TestOnly
  fun addLsp4jServerWrapper(wrapper: Lsp4jServerWrapper, parentDisposable: Disposable)

  @ApiStatus.Internal
  fun addLspServerManagerListener(
    listener: LspServerManagerListener,
    parentDisposable: Disposable,
    sendEventsForExistingServers: Boolean = false,
  )
}

inline fun <reified Provider : LspClientProvider> LspClientManager.getClientsForProvider(): Collection<LspClient> =
  getClientsForProvider(Provider::class.java)

inline fun <reified Provider : LspClientProvider> LspClientManager.startClientsIfNeeded(): Unit =
  startClientsIfNeeded(Provider::class.java)

inline fun <reified Provider : LspClientProvider> LspClientManager.ensureClientStarted(descriptor: LspClientDescriptor): Unit =
  ensureClientStarted(Provider::class.java, descriptor)

inline fun <reified Provider : LspClientProvider> LspClientManager.stopClients(): Unit =
  stopClients(Provider::class.java)

inline fun <reified Provider : LspClientProvider> LspClientManager.stopAndRestartClientsIfNeeded(): Unit =
  stopAndRestartClientsIfNeeded(Provider::class.java)
