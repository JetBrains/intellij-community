package com.intellij.platform.lsp.impl.lsWidget

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lang.lsWidget.LanguageServiceWidgetItem
import com.intellij.platform.lang.lsWidget.LanguageServiceWidgetItemsProvider
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.LspClientManagerListener
import com.intellij.platform.lsp.api.LspIntegrationProvider

internal class LspWidgetItemsProvider : LanguageServiceWidgetItemsProvider() {

  override fun createWidgetItems(project: Project, currentFile: VirtualFile?): List<LanguageServiceWidgetItem> =
    LspIntegrationProvider.getAllExtensions().flatMap { it.createWidgetItems(project, currentFile) }.toList()

  override fun registerWidgetUpdaters(project: Project, widgetDisposable: Disposable, updateWidget: () -> Unit) {
    LspClientManager.getInstance(project).addListener(
      listener = object : LspClientManagerListener {
        override fun serverStateChanged(lspClient: LspClient) = updateWidget()
      },
      parentDisposable = widgetDisposable,
      sendEventsForExistingClients = false,
    )
  }
}
