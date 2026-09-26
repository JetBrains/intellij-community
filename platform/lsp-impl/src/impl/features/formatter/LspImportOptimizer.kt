package com.intellij.platform.lsp.impl.features.formatter

import com.intellij.formatting.service.CoreFormattingService
import com.intellij.formatting.service.FormattingService
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.lang.ImportOptimizer
import com.intellij.lang.LanguageImportStatements
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.customization.LspIntentionAction
import com.intellij.platform.lsp.api.customization.LspOptimizeImportsSupport
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.LspClientManagerImpl
import com.intellij.platform.lsp.impl.documentMapping
import com.intellij.platform.lsp.impl.features.intention.toCodeAction
import com.intellij.platform.lsp.impl.util.LspWorkspaceEditApplier
import com.intellij.psi.PsiFile
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionContext
import org.eclipse.lsp4j.CodeActionKind.SourceOrganizeImports
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.CodeActionTriggerKind
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.WorkspaceEdit

/**
 * There are two ways in which the Platform may come to [LspImportOptimizer]:
 * - via [FormattingService.EP_NAME] extension point and [LspFormattingService.getImportOptimizers]
 * - via [com.intellij.formatting.service.CoreFormattingService]
 *   and [com.intellij.lang.LanguageImportStatements.INSTANCE] (i.e., via [ImportOptimizer] extension point)
 *
 *   The Platform uses one path to calculate [com.intellij.codeInsight.actions.OptimizeImportsAction] availability
 *   and another path to call [com.intellij.codeInsight.actions.OptimizeImportsAction.actionPerformed].
 *
 * Implementations of [LspFormattingService] and [LspImportOptimizer] make sure that both paths are 100% equivalent.
 */
internal class LspImportOptimizer : ImportOptimizer {

  private val noResult = Runnable { }

  override fun supports(psiFile: PsiFile): Boolean = findClientToOptimizeImports(psiFile) != null

  override fun processFile(psiFile: PsiFile): Runnable {
    val virtualFile = psiFile.virtualFile?.takeIf { it !is VirtualFileWindow } ?: return noResult
    val lspClient = findClientToOptimizeImports(psiFile) ?: return noResult

    val codeAction = requestCodeAction(lspClient, virtualFile) ?: return noResult

    val intentionAction = LspIntentionAction(lspClient, codeAction)
    if (!intentionAction.isAvailable()) return noResult

    val command = codeAction.command
    // If codeAction.edit is null or effectively a no-op, handle Command in a synchronous way (see IJPL-235332)
    if ((codeAction.edit == null || codeAction.edit == WorkspaceEdit()) && command != null) {
      val edit = (lspClient as LspClientImpl).executeCommandExpectingWorkspaceEdit(command) ?: return noResult
      val applier = LspWorkspaceEditApplier.create(lspClient, edit) ?: return noResult
      return Runnable {
        applier.applyWorkspaceEdit()
      }
    }

    return Runnable {
      intentionAction.invoke(virtualFile)
    }
  }

  private fun requestCodeAction(lspClient: LspClient, virtualFile: VirtualFile): CodeAction? {
    val codeActionContext = CodeActionContext().apply {
      diagnostics = emptyList()
      triggerKind = CodeActionTriggerKind.Invoked
      only = listOf(SourceOrganizeImports)
    }

    // Send code action request per document (cell). For regular files, there's one document.
    val results = lspClient.documentMapping.getDocumentsInFileSync(virtualFile).mapNotNull { lspDocument ->
      val params = CodeActionParams(
        lspDocument.id,
        Range(Position(0, 0), Position(0, 0)), // doesn't matter
        codeActionContext,
      )

      // Only one code action supported as UI does not suppose selection of the one import
      // option from the list (like it is processed in VS Code)
      lspClient.sendRequestSync { it.textDocumentService.codeAction(params) }?.singleOrNull()?.toCodeAction()
    }

    return results.firstOrNull()
  }

}


internal fun findClientToOptimizeImports(psiFile: PsiFile): LspClient? {
  if (psiFile.project.isDefault) return null
  val virtualFile = psiFile.virtualFile?.takeIf { it !is VirtualFileWindow } ?: return null

  return LspClientManagerImpl.getInstanceImpl(psiFile.project).findRunningClient { lspClient ->
    lspClient.descriptor.isSupportedFile(virtualFile) && canClientOptimizeImports(lspClient, psiFile, virtualFile)
  }
}

private fun canClientOptimizeImports(lspClient: LspClient, psiFile: PsiFile, virtualFile: VirtualFile): Boolean {
  val optimizeImportsSupport =
    lspClient.descriptor.lspCustomization.optimizeImportsCustomizer as? LspOptimizeImportsSupport ?: return false
  // `LspImportOptimizer` should not come to the stage if any other `ImportOptimizer` wants to do its job for this file.
  // Need to check if there's any other `FormattingService`/`ImportOptimizer` that wants to handle this file.
  // `CoreFormattingService` is special, it is registered as the last one, and it iterates `ImportOptimizer` extensions.
  // The following logic is similar to `OptimizeImportsProcessor.collectOptimizers()` and
  // `CoreFormattingService.getImportOptimizers()`, but without checking `LspFormattingService` and `LspImportOptimizer`.
  val ideCanOptimizeImportsInThisFileItself =
    doesOtherFormattingServiceWantToWork(psiFile) || doesOtherImportOptimizerWantToWork(psiFile)
  return optimizeImportsSupport
    .shouldOptimizeImportsInThisFileExclusivelyByServer(lspClient, virtualFile, ideCanOptimizeImportsInThisFileItself)
}

// The logic is similar to `OptimizeImportsProcessor.collectOptimizers()`,
// but without checking `LspFormattingService` and `CoreFormattingService`.
private fun doesOtherFormattingServiceWantToWork(psiFile: PsiFile): Boolean =
  FormattingService.EP_NAME.extensionList.any {
    it !is LspFormattingService &&
    it !is CoreFormattingService &&
    it.getFeatures().contains(FormattingService.Feature.OPTIMIZE_IMPORTS) &&
    it.canFormat(psiFile, FormattingService.Feature.OPTIMIZE_IMPORTS) &&
    it.getImportOptimizers(psiFile).any { optimizer -> optimizer !is LspImportOptimizer && optimizer.supports(psiFile) }
  }

// This function checks all `ImportOptimizers` except `LspImportOptimizer`.
// The logic is similar to `CoreFormattingService.getImportOptimizers()`, but without checking `LspImportOptimizer`.
private fun doesOtherImportOptimizerWantToWork(psiFile: PsiFile): Boolean =
  LanguageImportStatements.INSTANCE.allForLanguageOrAny(psiFile.getLanguage()).any {
    it !is LspImportOptimizer && it.supports(psiFile)
  }
