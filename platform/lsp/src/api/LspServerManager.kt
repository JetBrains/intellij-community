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
 * Plugins that want to start LSP servers should implement [LspServerSupportProvider].
 *
 * See [LspServerSupportProvider.fileOpened] function documentation for information about starting an LSP server.
 */
interface LspServerManager {
  companion object {
    /**
     * Returns an `LspServerManager` instance for the given project. The passed `project` parameter must not be the 'default project'
     * (the fake one used to manage settings for new projects). So, some callers may need to check [Project.isDefault] before calling
     * `LspServerManager.getInstance(project)`.
     */
    @JvmStatic
    fun getInstance(project: Project): LspServerManager = project.service()
  }

  fun getServersForProvider(providerClass: Class<out LspServerSupportProvider>): Collection<LspServer>

  /**
   * This function is designed for cases like "a user has enabled some framework support in Settings." It notifies the `providerClass`
   * about the files that are open in the editor by scheduling the [LspServerSupportProvider.fileOpened] function calls. So, if the
   * [fileOpened][LspServerSupportProvider.fileOpened] function is implemented according to its documentation, this function guarantees
   * that all [LSP servers][LspServer] needed for the currently open files will get started.
   *
   * If one or more [LSP servers][LspServer] are already running, and all the files that are open in the editor are within the roots
   * of the running servers, then this function doesn't do anything.
   *
   * This function may be called from any thread.
   *
   * @see [LspServerSupportProvider.fileOpened]
   */
  fun startServersIfNeeded(providerClass: Class<out LspServerSupportProvider>)

  /**
   * This function starts an LSP server even if no files are currently open in the editor,
   * so consider using [startServersIfNeeded] instead.
   *
   * If an [LSP server][LspServer] is already running, and it has the same roots as the passed [descriptor],
   * then this function doesn't do anything.
   *
   * This function may be called from any thread.
   */
  fun ensureServerStarted(providerClass: Class<out LspServerSupportProvider>, descriptor: LspServerDescriptor)

  /**
   * This function is designed for cases like "a user has disabled some framework support in Settings."
   * It stops all running LSP servers associated with the `providerClass`.
   */
  fun stopServers(providerClass: Class<out LspServerSupportProvider>)

  /**
   * Just a shorthand for [stopServers] followed by [startServersIfNeeded]. Typically, a plugin calls this function when, for example, a
   * user has changed some framework-specific settings, and therefore, an LSP server needs to be restarted with some other parameters.
   */
  fun stopAndRestartIfNeeded(providerClass: Class<out LspServerSupportProvider>)

  @ApiStatus.Internal
  fun addLspServerManagerListener(
    listener: LspServerManagerListener,
    parentDisposable: Disposable,
    sendEventsForExistingServers: Boolean = false,
  )

  @ApiStatus.Internal
  @TestOnly
  fun addLsp4jServerWrapper(
    wrapper: Lsp4jServerWrapper,
    parentDisposable: Disposable,
  )
}

inline fun <reified Provider : LspServerSupportProvider> LspServerManager.getServersForProvider(): Collection<LspServer> =
  getServersForProvider(Provider::class.java)

inline fun <reified Provider : LspServerSupportProvider> LspServerManager.startServersIfNeeded(): Unit =
  startServersIfNeeded(Provider::class.java)

inline fun <reified Provider : LspServerSupportProvider> LspServerManager.ensureServerStarted(descriptor: LspServerDescriptor): Unit =
  ensureServerStarted(Provider::class.java, descriptor)

inline fun <reified Provider : LspServerSupportProvider> LspServerManager.stopServers(): Unit =
  stopServers(Provider::class.java)

inline fun <reified Provider : LspServerSupportProvider> LspServerManager.stopAndRestartIfNeeded(): Unit =
  stopAndRestartIfNeeded(Provider::class.java)
