// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.api

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.ExtensionPointName.Companion.create
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lang.lsWidget.LanguageServiceWidgetItem
import com.intellij.platform.lsp.api.lsWidget.LspClientWidgetItem
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus

/**
 * Plugins register their implementations of the `LspIntegrationProvider` to add LSP-based support for some programming language
 * or framework. In [LSP terminology](https://microsoft.github.io/language-server-protocol/) the IDE is the client, which is why
 * the IDE-side [LspClient] is named after the client rather than the server.
 *
 * @see [fileOpened]
 * @see [https://microsoft.github.io/language-server-protocol/](https://microsoft.github.io/language-server-protocol/)
 */
interface LspIntegrationProvider {
  /**
   * Instance of [LspClientStarter] is passed to the plugin's implementation of the [LspIntegrationProvider.fileOpened] function.
   * If needed, the plugin may call [LspClientStarter.ensureClientStarted].
   *
   * Note that calling [LspClientStarter.ensureClientStarted]
   * after [LspIntegrationProvider.fileOpened] function has exited won't have any effect.
   * So, plugins should not store references to [LspClientStarter] instances.
   *
   * @see [LspIntegrationProvider.fileOpened]
   */
  interface LspClientStarter {
    /**
     * Looks for an already running [LspClient] that has the same roots as the passed [descriptor].
     * If such a client is found, then this function does nothing.
     * If not, then a new [LspClient] is created and started, using the passed [descriptor] to control its startup and behavior.
     *
     * Note that calling [LspClientStarter.ensureClientStarted]
     * after [LspIntegrationProvider.fileOpened] function has exited won't have any effect.
     * So, plugins should not store references to [LspClientStarter] instances.
     *
     * Tip: for a running [LspClient], the [descriptor] object that was used to start it is available as [LspClient.descriptor].
     */
    fun ensureClientStarted(descriptor: LspClientDescriptor)
  }

  /**
   * This function is a convenient way for the `LspIntegrationProvider` implementation to start an LSP client lazily, only when
   * needed. `fileOpened()` is invoked each time when a file is opened in the editor, unless an already running
   * [LspClient] exists and the opened file is within the server roots.
   *
   * Implementations may check the file type, plugin-specific settings, and call [LspClientStarter.ensureClientStarted] if needed.
   *
   * Typical implementation:
   *
   *    if (file.extension == "foo" && isFooSdkConfigured(project)) {
   *       // class FooLspClientDescriptor extends ProjectWideLspClientDescriptor
   *       clientStarter.ensureClientStarted(FooLspClientDescriptor(project))
   *    }
   *
   * Note that calling [LspClientStarter.ensureClientStarted] after [fileOpened] function has exited won't have any effect.
   * So, plugins should not store references to [LspClientStarter] instances.
   *
   * Plugins may want to start an LSP client not on 'file opened in the editor' event but on some other event, for example, on enabling a
   * plugin-specific framework support in Settings. In this case, plugins can call [LspClientManager.startClientsIfNeeded] function.
   *
   * Some plugins may want to perform a time-consuming task before starting an LSP client,
   * for example, download server binaries from the internet.
   * [fileOpened] function implementation shouldn't be time-consuming, as it may freeze UI.
   * Here's an example of running a time-consuming task in a background thread,
   * and starting an LSP client later using [LspClientManager.startClientsIfNeeded]:
   *
   *    override fun fileOpened(project: Project, file: VirtualFile, clientStarter: LspIntegrationProvider.LspClientStarter) {
   *      val fooService = FooService.getInstance(project)
   *      if (file.extension != "foo" || !fooService.isFooSupportEnabled) return
   *
   *      if (fooService.isLspServerDownloaded) {
   *        clientStarter.ensureClientStarted(FooLspClientDescriptor(project))
   *      }
   *      else if (!fooService.isDownloadAlreadyScheduled) {
   *        fooService.scheduleDownload()
   *      }
   *    }
   *
   *    // An example of the FooService.scheduleDownload() implementation:
   *    fun scheduleDownload() {
   *      if (isLspServerDownloaded || !isDownloadAlreadyScheduled.compareAndSet(false, true)) return
   *
   *      ApplicationManager.getApplication().executeOnPooledThread {
   *        downloadLspServer() // sets `isLspServerDownloaded` to `true` on success
   *        isDownloadAlreadyScheduled.set(false)
   *        LspClientManager.getInstance(project).startClientsIfNeeded(FooLspProvider::class.java)
   *      }
   *    }
   *
   * @param file a valid local file within the project roots
   */
  @RequiresReadLock
  @RequiresBackgroundThread
  fun fileOpened(project: Project, file: VirtualFile, clientStarter: LspClientStarter)

  fun createWidgetItems(project: Project, currentFile: VirtualFile?): List<LanguageServiceWidgetItem> =
    LspClientManager.getInstance(project).getClients(javaClass).mapNotNull { createWidgetItem(it, currentFile) }

  /**
   * Creates an LSP client-specific item in the 'Language Services' status bar widget.
   * The plugins are strongly recommended to override this function to have the correct icon
   * and the 'Open plugin page in Settings' action on click.
   *
   * Typical implementation:
   *
   *    override fun createWidgetItem(lspClient: LspClient, currentFile: VirtualFile?) =
   *      LspClientWidgetItem(lspClient, currentFile, fooIcon, FooConfigurable::class.java)
   */
  fun createWidgetItem(lspClient: LspClient, currentFile: VirtualFile?): LspClientWidgetItem? =
    LspClientWidgetItem(lspClient, currentFile)


  companion object {
    val EP_NAME: ExtensionPointName<LspIntegrationProvider> = create("com.intellij.platform.lsp.integrationProvider")

    /**
     * Enumerates providers from both the canonical [EP_NAME] and the deprecated [LspServerSupportProvider.EP_NAME].
     * Since [LspServerSupportProvider] is an [LspIntegrationProvider], callers can treat all providers uniformly.
     */
    @ApiStatus.Internal
    fun getAllExtensions(): Sequence<LspIntegrationProvider> = sequence {
      yieldAll(EP_NAME.extensionList)
      @Suppress("DEPRECATION")
      yieldAll(LspServerSupportProvider.EP_NAME.extensionList)
    }

    @ApiStatus.Internal
    fun hasAnyExtensions(): Boolean {
      return EP_NAME.hasAnyExtensions() ||
             @Suppress("DEPRECATION")
             LspServerSupportProvider.EP_NAME.hasAnyExtensions()
    }
  }
}
