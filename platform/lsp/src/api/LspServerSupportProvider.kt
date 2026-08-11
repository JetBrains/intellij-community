// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.api

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.ExtensionPointName.Companion.create
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lang.lsWidget.LanguageServiceWidgetItem
import com.intellij.platform.lsp.api.lsWidget.LspClientWidgetItem
import com.intellij.platform.lsp.api.lsWidget.LspServerWidgetItem
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock

@Suppress("DEPRECATION")
@Deprecated(
  "Renamed to LspIntegrationProvider",
  ReplaceWith("LspIntegrationProvider", "com.intellij.platform.lsp.api.LspIntegrationProvider"),
)
interface LspServerSupportProvider : LspIntegrationProvider {
  @Deprecated(
    "Renamed to LspIntegrationProvider.LspClientStarter",
    ReplaceWith("LspIntegrationProvider.LspClientStarter", "com.intellij.platform.lsp.api.LspIntegrationProvider"),
  )
  interface LspServerStarter {
    fun ensureServerStarted(descriptor: LspServerDescriptor)
  }

  @RequiresReadLock
  @RequiresBackgroundThread
  fun fileOpened(project: Project, file: VirtualFile, serverStarter: LspServerStarter)

  /**
   * Defers to [LspIntegrationProvider]'s enumeration via `super` (so the [createWidgetItems] override below isn't re-entered).
   * That enumeration maps each client through [createWidgetItem], so overriding the new [createWidgetItem] *or* the
   * deprecated [createLspServerWidgetItem] both take effect.
   */
  fun createLspWidgetItems(project: Project, currentFile: VirtualFile?): List<LanguageServiceWidgetItem> =
    super.createWidgetItems(project, currentFile)

  fun createLspServerWidgetItem(lspServer: LspServer, currentFile: VirtualFile?): LspServerWidgetItem? =
    LspServerWidgetItem(lspServer, currentFile)

  override fun fileOpened(project: Project, file: VirtualFile, clientStarter: LspIntegrationProvider.LspClientStarter): Unit =
    fileOpened(project, file, clientStarter as LspServerStarter)

  override fun createWidgetItems(project: Project, currentFile: VirtualFile?): List<LanguageServiceWidgetItem> =
    createLspWidgetItems(project, currentFile)

  override fun createWidgetItem(lspClient: LspClient, currentFile: VirtualFile?): LspClientWidgetItem? =
    createLspServerWidgetItem(lspClient as LspServer, currentFile)

  companion object {
    @Deprecated(
      "Use LspIntegrationProvider.EP_NAME",
      ReplaceWith("LspIntegrationProvider.EP_NAME", "com.intellij.platform.lsp.api.LspIntegrationProvider"),
    )
    val EP_NAME: ExtensionPointName<LspServerSupportProvider> = create("com.intellij.platform.lsp.serverSupportProvider")
  }
}
