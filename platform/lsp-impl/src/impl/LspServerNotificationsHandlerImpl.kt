package com.intellij.platform.lsp.impl

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.folding.CodeFoldingManager
import com.intellij.execution.process.AnsiEscapeDecoder
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.readAndEdtWriteAction
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.command.writeCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.lsp.api.LspBundle
import com.intellij.platform.lsp.api.LspServerNotificationsHandler
import com.intellij.platform.lsp.impl.features.LspFeaturesRefreshing
import com.intellij.platform.lsp.impl.util.LspWorkspaceEditApplier
import com.intellij.platform.lsp.util.getOffsetInDocument
import com.intellij.platform.util.progress.reportRawProgress
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.eclipse.lsp4j.ApplyWorkspaceEditParams
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse
import org.eclipse.lsp4j.ConfigurationParams
import org.eclipse.lsp4j.LogTraceParams
import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.ProgressParams
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.RegistrationParams
import org.eclipse.lsp4j.ShowDocumentParams
import org.eclipse.lsp4j.ShowDocumentResult
import org.eclipse.lsp4j.ShowMessageRequestParams
import org.eclipse.lsp4j.UnregistrationParams
import org.eclipse.lsp4j.WorkDoneProgressBegin
import org.eclipse.lsp4j.WorkDoneProgressCancelParams
import org.eclipse.lsp4j.WorkDoneProgressCreateParams
import org.eclipse.lsp4j.WorkDoneProgressEnd
import org.eclipse.lsp4j.WorkDoneProgressReport
import org.eclipse.lsp4j.WorkspaceFolder
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.completedFuture
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

internal class LspServerNotificationsHandlerImpl(private val lspClient: LspClientImpl) : LspServerNotificationsHandler {
  val project = lspClient.project

  private data class ProgressTask(
    val text: @NlsSafe String,
    val details: @NlsSafe String? = null,
    val fraction: Double? = null,
  )

  private val progressTasks = ConcurrentHashMap<String, ProgressTask>()
  private val progressJobs = ConcurrentHashMap<String, Job>()
  private val ansiDecoder = AnsiEscapeDecoder()

  override fun applyEdit(params: ApplyWorkspaceEditParams): CompletableFuture<ApplyWorkspaceEditResponse> {
    val future = CompletableFuture<ApplyWorkspaceEditResponse>()

    LspClientManagerImpl.getInstanceImpl(project).cs.launch {
      try {
        readAndEdtWriteAction {
          val applier = LspWorkspaceEditApplier.create(lspClient, params.edit)
                        ?: return@readAndEdtWriteAction value(Unit)
          @Suppress("HardCodedStringLiteral")
          val commandName = params.label
                            ?: LspBundle.message("code.change.from.server", lspClient.descriptor.presentableName)
          writeCommandAction(project, commandName) {
            applier.applyWorkspaceEdit()
            future.complete(ApplyWorkspaceEditResponse(true))
            Unit
          }
        }
      }
      finally {
        if (!future.isDone) {
          future.complete(ApplyWorkspaceEditResponse(false))
        }
      }
    }

    return future
  }

  override fun registerCapability(params: RegistrationParams): CompletableFuture<Void> {
    params.registrations.forEach { lspClient.dynamicCapabilities.registerCapability(it) }
    restartHighlightingIfNeeded(params.registrations.map { it.method })
    return completedFuture(null)
  }

  override fun unregisterCapability(params: UnregistrationParams): CompletableFuture<Void> {
    params.unregisterations.forEach { lspClient.dynamicCapabilities.unregisterCapability(it) }
    return completedFuture(null)
  }

  private fun restartHighlightingIfNeeded(registeredCapabilities: List<String>) {
    val needsInlayHintRefresh = registeredCapabilities.any {
      it == LspDynamicCapabilities.inlayHint.first ||
      it == LspDynamicCapabilities.documentColor.first
    }

    val needsFoldingUpdate = registeredCapabilities.any {
      it == LspDynamicCapabilities.foldingRange.first
    }

    val needsDaemonRestart = registeredCapabilities.any {
      it == LspDynamicCapabilities.documentLink.first ||
      it == LspDynamicCapabilities.diagnostic.first ||
      it == LspDynamicCapabilities.documentHighlight.first
    }

    val needsCodeLensesRefresh = registeredCapabilities.any {
      it == LspDynamicCapabilities.codeLens.first
    }

    if (needsInlayHintRefresh) {
      lspClient.refreshInlayHints()
    }

    if (needsCodeLensesRefresh) {
      LspFeaturesRefreshing.refreshCodeLenses(project)
    }

    if (needsFoldingUpdate) {
      for (fileEditor in FileEditorManager.getInstance(project).getAllEditors()) {
        if (fileEditor is TextEditor) {
          // Also calls `DaemonCodeAnalyzer.restart` internally
          CodeFoldingManager.getInstance(project).scheduleAsyncFoldingUpdate(fileEditor.editor)
        }
      }
    }

    // Folding update already restarts the daemon internally, so only restart explicitly when it wasn't triggered.
    if (needsDaemonRestart && !needsFoldingUpdate) {
      DaemonCodeAnalyzer.getInstance(project).restart("LspClientManagerImpl.registerCapabilities")
    }
  }

  override fun telemetryEvent(`object`: Any) {}

  override fun publishDiagnostics(params: PublishDiagnosticsParams) {
    if (!project.isDisposed) lspClient.diagnosticsReceived(params)
  }

  override fun showDocument(params: ShowDocumentParams): CompletableFuture<ShowDocumentResult> {
    val uri = params.uri

    @Suppress("HttpUrlsUsage")
    if (uri.startsWith("http://") || uri.startsWith("https://")) {
      BrowserUtil.browse(uri, project)
      return completedFuture(ShowDocumentResult(true))
    }

    lspClient.descriptor.findFileByUri(uri)?.let { file ->
      return openFile(file, focusEditor = params.takeFocus != false, params.selection)
    }

    return completedFuture(ShowDocumentResult(false))
  }

  private fun openFile(file: VirtualFile, focusEditor: Boolean, selection: Range?): CompletableFuture<ShowDocumentResult> {
    val future = CompletableFuture<ShowDocumentResult>()

    runInEdt {
      try {
        if (file.isValid) {
          if (selection != null) {
            val document = FileDocumentManager.getInstance().getDocument(file)
            if (document != null) {
              val startOffset = getOffsetInDocument(document, selection.start)
              val endOffset = getOffsetInDocument(document, selection.end)
              if (startOffset != null && endOffset != null) {
                val editor =
                  FileEditorManager.getInstance(project).openTextEditor(OpenFileDescriptor(project, file, startOffset), focusEditor)
                editor?.takeIf { startOffset != endOffset }?.selectionModel?.setSelection(startOffset, endOffset)
                future.complete(ShowDocumentResult(editor != null))
                return@runInEdt
              }
            }
          }

          val editor = FileEditorManager.getInstance(project).openTextEditor(OpenFileDescriptor(project, file), focusEditor)
          future.complete(ShowDocumentResult(editor != null))
        }
      }
      finally {
        if (!future.isDone) {
          future.complete(ShowDocumentResult(false))
        }
      }
    }

    return future
  }

  override fun workspaceFolders(): CompletableFuture<List<WorkspaceFolder>> {
    if (project.isDisposed) return completedFuture(emptyList())

    return completedFuture(lspClient.descriptor.roots.map { root ->
      WorkspaceFolder(lspClient.descriptor.getFileUri(root), root.name)
    })
  }

  override fun configuration(params: ConfigurationParams): CompletableFuture<List<Any?>> {
    if (project.isDisposed) return completedFuture(Collections.emptyList())

    return completedFuture(params.items.map { lspClient.descriptor.getWorkspaceConfiguration(it) })
  }

  override fun createProgress(params: WorkDoneProgressCreateParams): CompletableFuture<Void> = completedFuture(null)

  override fun notifyProgress(params: ProgressParams) {
    if (project.isDisposed) return

    val token = params.token
    val tokenId = token.map({ it }, { it.toString() })

    fun Int?.toFraction() = this?.let { (it / 100.0).coerceIn(0.0, 1.0) }

    when (val value = params.value.left) {
      is WorkDoneProgressBegin -> {
        progressJobs[tokenId]?.cancel()

        val job = LspClientManagerImpl.getInstanceImpl(project).cs.launch {

          progressTasks[tokenId] = ProgressTask(
            text = value.title,
            details = value.message,
            fraction = value.percentage.toFraction(),
          )

          withBackgroundProgress(project,
                                 LspBundle.message("progress.title.progress", lspClient.descriptor.presentableName),
                                 cancellable = value.cancellable ?: false) {

            coroutineContext.job.invokeOnCompletion { throwable ->
              if (throwable is CancellationException && value.cancellable == true) {
                lspClient.sendNotification { it.cancelProgress(WorkDoneProgressCancelParams(token)) }
              }
              progressJobs.remove(tokenId)
            }

            reportRawProgress { reporter ->
              while (true) {
                val currentState = progressTasks[tokenId] ?: break
                reporter.fraction(currentState.fraction)
                reporter.text(currentState.text)
                reporter.details(currentState.details)
                delay(100)
              }
            }
          }
        }

        progressJobs[tokenId] = job
      }
      is WorkDoneProgressReport -> {
        progressTasks.computeIfPresent(tokenId) { _, currentState ->
          currentState.copy(
            details = value.message ?: currentState.details,
            fraction = value.percentage.toFraction() ?: currentState.fraction
          )
        }
      }
      is WorkDoneProgressEnd -> {
        progressTasks.remove(tokenId)
        progressJobs.remove(tokenId)?.cancel()
      }
    }
  }

  override fun refreshSemanticTokens(): CompletableFuture<Void> {
    if (!project.isDisposed) {
      lspClient.refreshSemanticTokens()
    }
    return completedFuture(null)
  }

  override fun refreshCodeLenses(): CompletableFuture<Void> {
    LspFeaturesRefreshing.refreshCodeLenses(project)
    return completedFuture(null)
  }

  override fun refreshInlayHints(): CompletableFuture<Void> {
    if (!project.isDisposed) {
      lspClient.refreshInlayHints()
    }
    return completedFuture(null)
  }

  override fun refreshInlineValues(): CompletableFuture<Void> = completedFuture(null)
  override fun refreshDiagnostics(): CompletableFuture<Void> = completedFuture(null)


  override fun showMessageRequest(params: ShowMessageRequestParams): CompletableFuture<MessageActionItem> {
    if (project.isDisposed) return completedFuture(null)

    lspClient.logInfo("window/showMessageRequest: ${params.message}: ${params.actions?.joinToString { it.title }}")
    return doNotify(params.message, getNotificationType(params), SHOW_MESSAGE_NOTIFICATION_GROUP, params.actions)
  }

  override fun showMessage(params: MessageParams) {
    if (project.isDisposed) return

    lspClient.logInfo("window/showMessage: ${params.message}")
    doNotify(params.message, getNotificationType(params), SHOW_MESSAGE_NOTIFICATION_GROUP)
  }

  override fun logMessage(params: MessageParams) {
    if (project.isDisposed) return

    lspClient.logInfo("window/logMessage ${params.type}: ${params.message}")
    if (params.type == MessageType.Error || params.type == MessageType.Warning) {
      doNotify(params.message, getNotificationType(params), LOG_ERRORS_WARNINGS_NOTIFICATION_GROUP)
    }
    else {
      // Do not spam user with all the logs from the server. LOG_INFO_TRACE_NOTIFICATION_GROUP is silent by default.
      doNotify(params.message, NotificationType.INFORMATION, LOG_INFO_TRACE_NOTIFICATION_GROUP)
    }
  }

  override fun logTrace(params: LogTraceParams) {
    if (project.isDisposed) return

    // no need to LOG.info() it additionally; LOG.debug() done in Lsp4jServerConnector.createMessageJsonHandler is enough.
    val message = if (params.verbose != null) "${params.message}\n${params.verbose}" else params.message
    doNotify(message, NotificationType.INFORMATION, LOG_INFO_TRACE_NOTIFICATION_GROUP)
  }

  private fun getNotificationType(params: MessageParams): NotificationType = when (params.type) {
    MessageType.Error -> NotificationType.ERROR
    MessageType.Warning -> NotificationType.WARNING
    MessageType.Info, MessageType.Log -> NotificationType.INFORMATION
  }

  private fun doNotify(
    @NlsSafe message: String,
    type: NotificationType,
    notificationGroup: String,
    actionItems: List<MessageActionItem>? = null,
  ): CompletableFuture<MessageActionItem> {
    val result = CompletableFuture<MessageActionItem>()

    var cleanedMessage = ""
    // STDOUT - is the most appropriate option as we are processing notification messages (which are typically like stdout).
    ansiDecoder.escapeText(message, ProcessOutputTypes.STDOUT) { text, _ ->
      cleanedMessage += text
    }

    val presentableMessage: @NlsSafe String = "${lspClient.descriptor.presentableName}: $cleanedMessage"
    NotificationGroupManager.getInstance()
      .getNotificationGroup(notificationGroup)
      .createNotification(presentableMessage, type)
      .also { notification ->
        actionItems?.forEach { actionItem ->
          @Suppress("HardCodedStringLiteral")
          val actionLabel: @NlsSafe String = actionItem.title
          notification.addAction(object : AnAction(actionLabel) {
            override fun actionPerformed(e: AnActionEvent) {
              notification.expire()
              result.complete(actionItem)
            }
          })
        }
      }
      .notify(project)

    return result
  }

  companion object {
    /**
     * Default behavior: show balloon and write to the Notifications tool window.
     * The value of this string must be equal to the `notificationGroup` id in the `intellij.platform.lsp.xml` file.
     */
    const val SHOW_MESSAGE_NOTIFICATION_GROUP = "LSP window/showMessage"

    /**
     * Default behavior: no balloon, only write to the Notifications tool window.
     * The value of this string must be equal to the `notificationGroup` id in the `intellij.platform.lsp.xml` file.
     */
    private const val LOG_ERRORS_WARNINGS_NOTIFICATION_GROUP = "LSP window/logMessage: errors, warnings"

    /**
     * Default behavior: no notification. For development purposes, plugin developers may enable printing to the Notifications tool window.
     * The value of this string must be equal to the `notificationGroup` id in the `intellij.platform.lsp.xml` file.
     */
    private const val LOG_INFO_TRACE_NOTIFICATION_GROUP = "LSP window/logMessage: info, log; $/logTrace"
  }
}
