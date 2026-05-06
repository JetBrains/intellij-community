// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.api

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.ExtensionPointName.Companion.create
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lang.lsWidget.LanguageServiceWidgetItem
import com.intellij.platform.lsp.api.lsWidget.LspServerWidgetItem
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock

/**
 * Plugins register their implementations of the `LspServerSupportProvider` to add LSP server-based support for some programming language
 * or framework.
 *
 * @see [fileOpened]
 * @see [https://microsoft.github.io/language-server-protocol/](https://microsoft.github.io/language-server-protocol/)
 */
interface LspServerSupportProvider {
  /**
   * Instance of [LspServerStarter] is passed to the plugin's implementation of the [LspServerSupportProvider.fileOpened] function.
   * If needed, the plugin may call [LspServerStarter.ensureServerStarted].
   *
   * Note that calling [LspServerStarter.ensureServerStarted]
   * after [LspServerSupportProvider.fileOpened] function has exited won't have any effect.
   * So, plugins should not store references to [LspServerStarter] instances.
   *
   * @see [LspServerSupportProvider.fileOpened]
   */
  interface LspServerStarter {
    /**
     * Looks for an already running [LSP server][LspServer] that has the same roots as the passed [LspServerDescriptor][descriptor].
     * If such a server is found, then this function does nothing.
     * If not, then a new instance of [LspServer] is created and started,
     * the passed [LspServerDescriptor][descriptor] is used to control the server startup and behavior.
     *
     * Note that calling [LspServerStarter.ensureServerStarted]
     * after [LspServerSupportProvider.fileOpened] function has exited won't have any effect.
     * So, plugins should not store references to [LspServerStarter] instances.
     *
     * Tip: for a running [LspServer], the [descriptor] object that was used to start it is available as [LspServer.descriptor].
     */
    fun ensureServerStarted(descriptor: LspServerDescriptor)
  }

  /**
   * This function is a convenient way for the `LspServerSupportProvider` implementation to start an LSP server lazily, only when
   * needed. `fileOpened()` is invoked each time when a file is opened in the editor, unless an already running
   * [LspServer] exists and the opened file is within the server roots.
   *
   * Implementations may check the file type, plugin-specific settings, and call [LspServerStarter.ensureServerStarted] if needed.
   *
   * Typical implementation:
   *
   *    if (file.extension == "foo" && isFooSdkConfigured(project)) {
   *       // class FooLspServerDescriptor extends ProjectWideLspServerDescriptor
   *       serverStarter.ensureServerStarted(FooLspServerDescriptor(project))
   *    }
   *
   * Note that calling [LspServerStarter.ensureServerStarted] after [fileOpened] function has exited won't have any effect.
   * So, plugins should not store references to [LspServerStarter] instances.
   *
   * Plugins may want to start an LSP server not on 'file opened in the editor' event but on some other event, for example, on enabling a
   * plugin-specific framework support in Settings. In this case, plugins can call [LspServerManager.startServersIfNeeded] function.
   *
   * Some plugins may want to perform a time-consuming task before starting an LSP server,
   * for example, download server binaries from the internet.
   * [fileOpened] function implementation shouldn't be time-consuming, as it may freeze UI.
   * Here's an example of running a time-consuming task in a background thread,
   * and starting an LSP server later using [LspServerManager.startServersIfNeeded]:
   *
   *    override fun fileOpened(project: Project, file: VirtualFile, serverStarter: LspServerSupportProvider.LspServerStarter) {
   *      val fooService = FooService.getInstance(project)
   *      if (file.extension != "foo" || !fooService.isFooSupportEnabled) return
   *
   *      if (fooService.isLspServerDownloaded) {
   *        serverStarter.ensureServerStarted(FooLspServerDescriptor(project))
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
   *        LspServerManager.getInstance(project).startServersIfNeeded(FooLspSupportProvider::class.java)
   *      }
   *    }
   *
   * @param file a valid local file within the project roots
   */
  @RequiresReadLock
  @RequiresBackgroundThread
  fun fileOpened(project: Project, file: VirtualFile, serverStarter: LspServerStarter)

  fun createLspWidgetItems(project: Project, currentFile: VirtualFile?): List<LanguageServiceWidgetItem> =
    LspServerManager.getInstance(project).getServersForProvider(javaClass).mapNotNull { createLspServerWidgetItem(it, currentFile) }

  /**
   * Creates an LSP server-specific item in the 'Language Services' status bar widget.
   * The plugins are strongly recommended to override this function to have the correct icon
   * and the 'Open plugin page in Settings' action on click.
   *
   * Typical implementation:
   *
   *    override fun getLspServerWidgetItem(lspServer: LspServer, currentFile: VirtualFile?) =
   *      LspServerWidgetItem(lspServer, currentFile, fooIcon, FooConfigurable::class.java)
   */
  fun createLspServerWidgetItem(lspServer: LspServer, currentFile: VirtualFile?): LspServerWidgetItem? =
    LspServerWidgetItem(lspServer, currentFile)


  companion object {
    val EP_NAME: ExtensionPointName<LspServerSupportProvider> = create("com.intellij.platform.lsp.serverSupportProvider")
  }
}
