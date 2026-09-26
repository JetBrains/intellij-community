package com.intellij.platform.lsp.impl.features.quickFix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.customization.LspCodeActionsSupport
import com.intellij.platform.lsp.api.customization.LspIntentionAction
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.features.intention.toCodeAction
import com.intellij.psi.PsiManager
import com.intellij.util.application
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionContext
import org.eclipse.lsp4j.CodeActionKind
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.CodeActionTriggerKind
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.TextDocumentIdentifier

/**
 * IntelliJ Platform API assumes that quick fixes for diagnostics are created right at the moment of the code highlighting. For
 * performance reasons, special [LspQuickFixWrapper] are created at that moment. In other words, [LspQuickFixWrapper] is a quick fix stub.
 * [LspQuickFixWrapper] doesn't contain a [CodeAction] object. As soon as the IntelliJ Platform calls [LspQuickFixWrapper.getText] or
 * [LspQuickFixWrapper.isAvailable], the [LspQuickFixWrapper] asks the LSP server to calculate real [CodeAction] and, once received, creates
 * [LspIntentionAction] object. After that, [LspQuickFixWrapper] delegates [IntentionAction.getText], [IntentionAction.isAvailable], and
 * [IntentionAction.invoke] calls to the [LspIntentionAction].
 */
internal class LspQuickFixSet(
  private val lspClient: LspClientImpl,
  private val file: VirtualFile,
  private val diagnostic: Diagnostic,
  private val diagnosticDocumentId: TextDocumentIdentifier? = null,
) {
  private val MAX_QUICK_FIXES = 8

  val quickFixes: List<IntentionAction> = List(MAX_QUICK_FIXES) { index ->
    LspQuickFixWrapper(this@LspQuickFixSet, index)
  }

  private var psiModCountWhenRequestSent: Long = 0
  private var vfsModCountWhenRequestSent: Long = 0

  internal fun ensureInitialized() {
    // IntentionAction.text and IntentionAction.isAvailable are normally called from BGT.
    // IntentionAction.invoke is called from EDT, but at that moment, this LspQuickFixWrapper should be already initialized.
    // Anyway, we are not going to wait for the response from the server in EDT.
    if (application.isDispatchThread) return

    // A synchronized block is needed to make sure that several threads don't send the same `textDocument/codeAction` request to the server
    // (there may be up to MAX_QUICK_FIXES parallel isAvailable() calls for MAX_QUICK_FIXES LspQuickFixWrapper objects)
    synchronized(this) {
      ProgressManager.checkCanceled()

      val psiModCount = PsiManager.getInstance(lspClient.project).modificationTracker.modificationCount
      val vfsModCount = VirtualFileManager.getInstance().modificationCount
      if (psiModCountWhenRequestSent == psiModCount && vfsModCountWhenRequestSent == vfsModCount) {
        return // already up-to-date
      }

      quickFixes.forEach { (it as LspQuickFixWrapper).lspIntentionAction = null }

      val codeActionContext = CodeActionContext().apply {
        only = listOf(CodeActionKind.QuickFix)
        diagnostics = listOf(diagnostic)
        triggerKind = CodeActionTriggerKind.Automatic
      }
      val params = if (diagnosticDocumentId != null) {
        // Cell URI known from diagnostic source — use it directly with the cell-relative range.
        // diagnostic.range is cell-relative here (as received from the server).
        CodeActionParams(diagnosticDocumentId, diagnostic.range, codeActionContext)
      }
      else {
        // No cell URI — regular text document. diagnostic.range is file-absolute,
        // so getDocumentRanges resolves it to the correct (single) document.
        val document = FileDocumentManager.getInstance().getDocument(file) ?: return
        val (lspDocument, cellRange) = lspClient.documentMapping
                                         .getDocumentRangesSync(file, document, diagnostic.range)
                                         .firstOrNull() ?: return
        CodeActionParams(lspDocument.id, cellRange, codeActionContext)
      }
      lspClient.requestExecutor
        .sendRequestAsyncButWaitForResponseWithCheckCanceled({ it.textDocumentService.codeAction(params) }) { lsp4jResults ->
          if (psiModCount == PsiManager.getInstance(lspClient.project).modificationTracker.modificationCount &&
              vfsModCount == VirtualFileManager.getInstance().modificationCount) {
            psiModCountWhenRequestSent = psiModCount
            vfsModCountWhenRequestSent = vfsModCount

            lsp4jResults?.let { codeActionsReceived(it.map { lsp4jResult -> lsp4jResult.toCodeAction() }) }
          }
        }
    }
  }

  /**
   * Creates [LspIntentionAction] objects, to which [LspQuickFixWrapper] will delegate [IntentionAction.getText],
   * [IntentionAction.isAvailable], and [IntentionAction.invoke]
   */
  private fun codeActionsReceived(codeActions: List<CodeAction>) {
    val codeActionsSupport = lspClient.descriptor.lspCustomization.codeActionsCustomizer as? LspCodeActionsSupport ?: return
    var i = 0
    for (codeAction in codeActions) {
      if (i == quickFixes.size) {
        thisLogger().info("Received ${codeActions.size} quick fixes from server, only ${quickFixes.size} will be handled")
        break
      }

      codeActionsSupport.createQuickFix(lspClient as LspClient, codeAction)?.let {
        (quickFixes[i++] as LspQuickFixWrapper).lspIntentionAction = it
      }
    }
  }
}

/**
 * @see [LspQuickFixSet]
 */
private class LspQuickFixWrapper(private val quickFixSet: LspQuickFixSet, index: Int) : LspIntentionActionWrapperBase(index) {
  override var lspIntentionAction: LspIntentionAction? = null
    get() {
      quickFixSet.ensureInitialized()
      return field
    }
}
