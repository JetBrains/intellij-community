package com.intellij.platform.lsp.impl.lsWidget

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lang.lsWidget.LanguageServiceWidgetItem
import com.intellij.platform.lang.lsWidget.LanguageServiceWidgetItemsProvider
import com.intellij.platform.lsp.api.LspServer
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.platform.lsp.api.LspServerManagerListener
import com.intellij.platform.lsp.api.LspServerSupportProvider

internal class LspWidgetItemsProvider : LanguageServiceWidgetItemsProvider() {

  override fun createWidgetItems(project: Project, currentFile: VirtualFile?): List<LanguageServiceWidgetItem> =
    LspServerSupportProvider.EP_NAME.extensionList.flatMap { it.createLspWidgetItems(project, currentFile) }

  override fun registerWidgetUpdaters(project: Project, widgetDisposable: Disposable, updateWidget: () -> Unit) {
    LspServerManager.getInstance(project).addLspServerManagerListener(
      listener = object : LspServerManagerListener {
        override fun serverStateChanged(lspServer: LspServer) = updateWidget()
      },
      parentDisposable = widgetDisposable,
      sendEventsForExistingServers = false,
    )
  }
}
