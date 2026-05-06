package com.intellij.platform.lsp.impl.lsWidget

import com.intellij.icons.AllIcons
import com.intellij.lang.LangBundle
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.project.DumbAware
import com.intellij.platform.lang.lsWidget.impl.fus.LanguageServiceWidgetActionKind
import com.intellij.platform.lang.lsWidget.impl.fus.LanguageServiceWidgetUsagesCollector
import com.intellij.platform.lsp.api.LspBundle
import com.intellij.platform.lsp.api.LspServer
import com.intellij.platform.lsp.api.lsWidget.LspWidgetInternalService
import com.intellij.platform.lsp.impl.LspServerImpl
import com.intellij.platform.lsp.impl.LspServerManagerImpl
import com.intellij.testFramework.LightVirtualFile

/**
 * Unlike [LspWidgetInternalService], this class is located in the `intellij.platform.lsp.impl` module,
 * so it has access to FUS-related classes, including [LanguageServiceWidgetUsagesCollector],
 * as well as to [LspServerImpl] and other classes that are not available in the public LSP API.
 */
internal class LspWidgetInternalServiceImpl : LspWidgetInternalService() {
  override fun createShowErrorOutputAction(lspServer: LspServer): AnAction? {
    (lspServer as LspServerImpl).errorOutput ?: return null
    return OpenLspErrorOutputAction(lspServer)
  }

  override fun restartLspServer(lspServer: LspServer) {
    LanguageServiceWidgetUsagesCollector
      .actionInvoked(lspServer.project, LanguageServiceWidgetActionKind.RestartService, lspServer.descriptor.javaClass)
    val manager = LspServerManagerImpl.getInstanceImpl(lspServer.project)
    manager.stopRunningServer(lspServer as LspServerImpl)
    manager.startServersIfNeeded(lspServer.providerClass)

    val title = LangBundle.message("language.services.0.server.restarted.notification.title", lspServer.descriptor.presentableName)
    NotificationGroupManager.getInstance()
      .getNotificationGroup("language.service.stopped.or.restarted")
      .createNotification(title, "", NotificationType.INFORMATION)
      .notify(lspServer.project)
  }

  override fun stopLspServer(lspServer: LspServer) {
    LanguageServiceWidgetUsagesCollector
      .actionInvoked(lspServer.project, LanguageServiceWidgetActionKind.StopService, lspServer.descriptor.javaClass)
    LspServerManagerImpl.getInstanceImpl(lspServer.project).stopRunningServer(lspServer as LspServerImpl)

    val title = LangBundle.message("language.services.0.server.stopped.notification.title", lspServer.descriptor.presentableName)
    val content = LangBundle.message("language.services.server.stopped.notification.content")
    NotificationGroupManager.getInstance()
      .getNotificationGroup("language.service.stopped.or.restarted")
      .createNotification(title, content, NotificationType.INFORMATION)
      .notify(lspServer.project)
  }
}

private class OpenLspErrorOutputAction(
  private val lspServer: LspServerImpl,
) : AnAction(LspBundle.message("action.OpenLspErrorOutputAction.text"), null, AllIcons.General.Error), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    val fileName = LspBundle.message("server.error.output.editor.tab.name", lspServer.descriptor.presentableName)
    val file = LightVirtualFile(fileName, PlainTextFileType.INSTANCE, lspServer.errorOutput ?: "")
    FileEditorManager.getInstance(lspServer.project).openTextEditor(OpenFileDescriptor(lspServer.project, file), true)
  }
}
