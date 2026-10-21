package com.intellij.platform.lsp.impl.features.intention

import com.intellij.codeInsight.intention.IntentionActionDelegate
import com.intellij.codeInsight.intention.IntentionManager
import com.intellij.injected.editor.EditorWindow
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.customization.LspCodeActionsSupport
import com.intellij.platform.lsp.api.customization.LspIntentionAction
import com.intellij.platform.lsp.impl.LspClientManagerImpl
import com.intellij.platform.lsp.impl.cache.LspPerFileCache
import com.intellij.platform.lsp.impl.supportsCodeActions
import com.intellij.platform.lsp.util.getLsp4jRange
import com.intellij.util.asSafely
import com.intellij.util.ui.EDT
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionContext
import org.eclipse.lsp4j.CodeActionKind
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.CodeActionTriggerKind
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either

@Service(Service.Level.PROJECT)
internal class LspIntentionActionService(private val project: Project) : Disposable {
  companion object {
    fun getInstance(project: Project): LspIntentionActionService = project.service()
  }

  private val intentionActionsCache = LspPerFileCache<Pair<Int, Int>, List<LspIntentionAction>>(project)

  fun getIntentionActions(editor: Editor): List<LspIntentionAction> {
    if (editor is EditorWindow) {
      thisLogger().error("EditorWindow not expected here")
      return emptyList()
    }

    val document = editor.document
    val file = FileDocumentManager.getInstance().getFile(document) ?: return emptyList()
    val currentCaret = editor.caretModel.primaryCaret
    val offset = currentCaret.selectionStart
    val length = currentCaret.selectionEnd - offset

    if (EDT.isCurrentThreadEdt()) {
      return intentionActionsCache.getIfComputed(file, offset to length) ?: emptyList()
    }

    return intentionActionsCache.getOrCompute(file, offset to length) {
      computeIntentionActions(file, document, offset, length)
    } ?: emptyList()
  }

  private fun computeIntentionActions(
    file: VirtualFile,
    document: Document,
    offset: Int,
    length: Int,
  ): List<LspIntentionAction> {
    val result = mutableListOf<LspIntentionAction>()

    for (lspClient in LspClientManagerImpl.getInstanceImpl(project).getClientsWithThisFileOpen(file)) {
      ProgressManager.checkCanceled()
      val codeActionsSupport = lspClient.descriptor.lspCustomization.codeActionsCustomizer
                                 .asSafely<LspCodeActionsSupport>()
                                 ?.takeIf { it.intentionActionsSupport }
                               ?: continue
      // When asking for Intentions, we are not interested in servers that support only quick fixes
      val somethingButQuickFix = lspClient.supportsCodeActions { kinds -> kinds.any { kind -> !kind.startsWith(CodeActionKind.QuickFix) } }
      if (!somethingButQuickFix) continue

      val hostRange = getLsp4jRange(document, offset, length)
      val codeActionContext = CodeActionContext().apply {
        diagnostics = emptyList()
        triggerKind = CodeActionTriggerKind.Automatic
      }

      val lspDocuments = lspClient.documentMapping.getDocumentRangesSync(file, document, hostRange)
      val lsp4jResults = lspDocuments.flatMap { (lspDocument, cellRange) ->
        val params = CodeActionParams(lspDocument.id, cellRange, codeActionContext)
        lspClient.sendRequestSync { it.textDocumentService.codeAction(params) } ?: emptyList()
      }

      lsp4jResults.forEach {
        val codeAction = it.toCodeAction()
        // filter out quick fixes when asking for intentions, otherwise quick fixes are added twice
        val kind = codeAction.kind
        if (kind == null || !kind.startsWith(CodeActionKind.QuickFix)) {
          codeActionsSupport.createIntentionAction(lspClient as LspClient, codeAction)?.let { result.add(it) }
        }
      }
    }
    return result
  }

  override fun dispose() {
    IntentionManager.getInstance().intentionActions
      .map { IntentionActionDelegate.unwrap(it) }
      .filterIsInstance<LspIntentionActionWrapper>()
      .forEach {
        // IntentionActions are app-level entities, need to drop project-specific caches
        it.lspIntentionAction = null
      }
  }
}


internal fun Either<Command, CodeAction>.toCodeAction(): CodeAction =
  if (isLeft) {
    CodeAction().apply {
      title = left!!.title
      command = left!!
      // Non-null `edit` guarantees that the IDE won't send the `codeAction/resolve` request to the server for this `CodeAction`
      // (see `LspIntentionAction.resolvedCodeAction`)
      edit = WorkspaceEdit()
    }
  }
  else right!!
