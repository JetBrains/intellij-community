package com.intellij.platform.lsp.impl.features.codeActions

import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.customization.LspCodeActionsSupport
import com.intellij.platform.lsp.api.customization.LspIntentionAction
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.features.intention.toCodeAction
import com.intellij.platform.lsp.util.getLsp4jRange
import com.intellij.psi.PsiManager
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionContext
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.CodeActionTriggerKind
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException

private data class ClientCodeAction(val lspClient: LspClientImpl, val intentionAction: LspIntentionAction)

/**
 * Requests the code actions of the given [kinds] from every client of [lspClients] for [offset] and [length],
 * which default to the caret or selection.
 * The [kinds] list is a preference order: the actions come from the first kind that yields any, and the later kinds are not requested.
 * A disabled action is not a result: it never stops that fallback, and its reason is the hint when no kind yields an enabled action.
 * The actions go through [LspCodeActionsSupport.createIntentionAction], so a plugin filters the refactoring shortcuts like the intention popup.
 * A deferred caller passes the position it captured at the invocation, so a later caret move cannot retarget the request.
 * Then it shows an error hint for zero results, applies a single result, or opens a chooser popup.
 * A client that fails the request contributes no actions; its error shows only when every client returns nothing.
 */
internal fun requestAndApplyCodeActions(
  lspClients: List<LspClientImpl>,
  editor: Editor,
  virtualFile: VirtualFile,
  kinds: List<String>,
  progressTitle: @NlsContexts.ModalProgressTitle String,
  noActionsMessage: @NlsContexts.HintText String,
  popupTitle: @NlsContexts.PopupTitle String,
  offset: Int = editor.caretModel.primaryCaret.selectionStart,
  length: Int = editor.caretModel.primaryCaret.selectionEnd - offset,
) {
  val project = lspClients.firstOrNull()?.project ?: return
  val document = editor.document

  var firstError: ResponseErrorException? = null
  var firstDisabledReason: String? = null
  val codeActions = runWithModalProgressBlocking(project, progressTitle) {
    kinds.firstNotNullOfOrNull { kind ->
      lspClients.flatMap { lspClient ->
        val codeActionsSupport = lspClient.descriptor.lspCustomization.codeActionsCustomizer as? LspCodeActionsSupport
                                 ?: return@flatMap emptyList()
        val received = try {
          requestCodeActions(lspClient, document, virtualFile, offset, length, kind)
        }
        catch (e: ResponseErrorException) {
          if (firstError == null) firstError = e
          emptyList()
        }
        val (disabled, enabled) = received.partition { it.disabled != null }
        if (firstDisabledReason == null) firstDisabledReason = disabled.firstOrNull()?.disabled?.reason?.takeIf { it.isNotBlank() }
        enabled.mapNotNull { codeAction ->
          codeActionsSupport.createIntentionAction(lspClient as LspClient, codeAction)?.let { ClientCodeAction(lspClient, it) }
        }
      }.takeIf { it.isNotEmpty() }
    } ?: emptyList()
  }

  when {
    codeActions.isEmpty() -> showErrorHint(editor, firstDisabledReason ?: firstError?.responseError?.message ?: noActionsMessage)
    codeActions.size == 1 -> applyCodeAction(codeActions.single(), editor, virtualFile, progressTitle, noActionsMessage)
    else -> JBPopupFactory.getInstance()
      .createPopupChooserBuilder(codeActions)
      .setTitle(popupTitle)
      .setRenderer(textListCellRenderer { it?.intentionAction?.text })
      .setItemChosenCallback { applyCodeAction(it, editor, virtualFile, progressTitle, noActionsMessage) }
      .createPopup()
      .showInBestPositionFor(editor)
  }
}

private suspend fun requestCodeActions(
  lspClient: LspClientImpl,
  document: Document,
  virtualFile: VirtualFile,
  offset: Int,
  length: Int,
  kind: String,
): List<CodeAction> {
  val lspDocumentRanges = readAction {
    val hostRange = getLsp4jRange(document, offset, length)
    lspClient.documentMapping.getDocumentRangesSync(virtualFile, document, hostRange)
  }
  val codeActionContext = CodeActionContext().apply {
    diagnostics = emptyList()
    only = listOf(kind)
    triggerKind = CodeActionTriggerKind.Invoked
  }
  val lsp4jResults = lspDocumentRanges.flatMap { (lspDocument, range) ->
    val params = CodeActionParams(lspDocument.id, range, codeActionContext)
    lspClient.sendRequest { it.textDocumentService.codeAction(params) } ?: emptyList()
  }
  // A bare `Command` and a kind-less `CodeAction` carry no kind to check; trust the request's `only` filter for them.
  // A kind counts only inside the requested dot-separated subtree: `refactor.inline2` does not answer `refactor.inline`.
  return lsp4jResults
    .filter { it.isLeft || it.right?.kind?.let { k -> k == kind || k.startsWith("$kind.") } != false }
    .map { it.toCodeAction() }
}

private fun applyCodeAction(
  clientCodeAction: ClientCodeAction,
  editor: Editor,
  virtualFile: VirtualFile,
  progressTitle: @NlsContexts.ModalProgressTitle String,
  noActionsMessage: @NlsContexts.HintText String,
) {
  val (lspClient, intentionAction) = clientCodeAction
  val project = lspClient.project
  // the IntentionAction overloads, like the intention wrapper calls them, so a subclass from `createIntentionAction` keeps its overrides;
  // the PSI file is looked up on each side, so none crosses the thread boundary
  val available = runWithModalProgressBlocking(project, progressTitle) {
    readAction {
      val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
      psiFile != null && intentionAction.isAvailable(project, editor, psiFile)
    }
  }
  val psiFile = if (available) PsiManager.getInstance(project).findFile(virtualFile) else null
  if (psiFile == null) {
    showErrorHint(editor, noActionsMessage)
    return
  }
  // a refactoring action handler, unlike an intention, gets no surrounding command from the platform
  @NlsSafe val commandName: String = intentionAction.text
  CommandProcessor.getInstance().executeCommand(project, { intentionAction.invoke(project, editor, psiFile) }, commandName, null)
}

internal fun showErrorHint(editor: Editor, errorMessage: @NlsSafe String) =
  HintManager.getInstance().showErrorHint(
    editor, errorMessage,
    editor.caretModel.offset, editor.caretModel.offset,
    HintManager.ABOVE,
    HintManager.HIDE_BY_ANY_KEY or HintManager.HIDE_BY_TEXT_CHANGE or HintManager.UPDATE_BY_SCROLLING,
    3000
  )
