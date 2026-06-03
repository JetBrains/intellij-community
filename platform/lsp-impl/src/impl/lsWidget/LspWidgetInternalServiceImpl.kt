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
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.lsWidget.LspWidgetInternalService
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.LspClientManagerImpl
import com.intellij.testFramework.LightVirtualFile

/**
 * Unlike [LspWidgetInternalService], this class is located in the `intellij.platform.lsp.impl` module,
 * so it has access to FUS-related classes, including [LanguageServiceWidgetUsagesCollector],
 * as well as to [LspClientImpl] and other classes that are not available in the public LSP API.
 */
internal class LspWidgetInternalServiceImpl : LspWidgetInternalService() {
  override fun createShowErrorOutputAction(lspClient: LspClient): AnAction? {
    val lspClient = lspClient as LspClientImpl
    lspClient.errorOutput ?: return null
    return OpenLspErrorOutputAction(lspClient)
  }

  override fun restartLspClient(lspClient: LspClient) {
    LanguageServiceWidgetUsagesCollector
      .actionInvoked(lspClient.project, LanguageServiceWidgetActionKind.RestartService, lspClient.descriptor.javaClass)
    val manager = LspClientManagerImpl.getInstanceImpl(lspClient.project)
    manager.stopRunningServer(lspClient as LspClientImpl)
    manager.startClientsIfNeeded(lspClient.providerClass)

    val title = LangBundle.message("language.services.0.server.restarted.notification.title", lspClient.descriptor.presentableName)
    NotificationGroupManager.getInstance()
      .getNotificationGroup("language.service.stopped.or.restarted")
      .createNotification(title, "", NotificationType.INFORMATION)
      .notify(lspClient.project)
  }

  override fun stopLspClient(lspClient: LspClient) {
    LanguageServiceWidgetUsagesCollector
      .actionInvoked(lspClient.project, LanguageServiceWidgetActionKind.StopService, lspClient.descriptor.javaClass)
    LspClientManagerImpl.getInstanceImpl(lspClient.project).stopRunningServer(lspClient as LspClientImpl)

    val title = LangBundle.message("language.services.0.server.stopped.notification.title", lspClient.descriptor.presentableName)
    val content = LangBundle.message("language.services.server.stopped.notification.content")
    NotificationGroupManager.getInstance()
      .getNotificationGroup("language.service.stopped.or.restarted")
      .createNotification(title, content, NotificationType.INFORMATION)
      .notify(lspClient.project)
  }
}

private class OpenLspErrorOutputAction(
  private val lspClient: LspClientImpl,
) : AnAction(LspBundle.message("action.OpenLspErrorOutputAction.text"), null, AllIcons.General.Error), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    val fileName = LspBundle.message("server.error.output.editor.tab.name", lspClient.descriptor.presentableName)
    val file = LightVirtualFile(fileName, PlainTextFileType.INSTANCE, lspClient.errorOutput ?: "")
    FileEditorManager.getInstance(lspClient.project).openTextEditor(OpenFileDescriptor(lspClient.project, file), true)
  }
}
