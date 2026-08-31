package com.intellij.platform.lsp.impl.features.inline

import com.intellij.lang.Language
import com.intellij.lang.refactoring.InlineActionHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.LspBundle
import com.intellij.platform.lsp.api.customization.LspCodeActionsSupport
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.LspClientManagerImpl
import com.intellij.platform.lsp.impl.features.codeActions.requestAndApplyCodeActions
import com.intellij.platform.lsp.impl.features.findPendingLspClient
import com.intellij.platform.lsp.impl.features.showLspServerNotReadyHint
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.eclipse.lsp4j.CodeActionKind

internal class LspInlineActionHandler : InlineActionHandler() {

  // `isEnabledOnElement` gates the action for real, the same way the ANY-language Extract providers do.
  override fun isEnabledForLanguage(l: Language): Boolean = true

  // an LSP code action needs a caret or a selection in an open document, so the Project view cannot invoke this handler
  override fun canInlineElement(element: PsiElement): Boolean = false

  override fun isEnabledOnElement(element: PsiElement, editor: Editor?): Boolean =
    editor != null && canInlineElementInEditor(element, editor)

  override fun canInlineElementInEditor(element: PsiElement, editor: Editor): Boolean {
    val psiFile = editorPsiFile(element.project, editor) ?: return false
    return findLspClientsForInline(psiFile).isNotEmpty() || findPendingLspClientForInline(psiFile) != null
  }

  override fun inlineElement(project: Project, editor: Editor?, element: PsiElement) {
    if (editor == null) return
    val psiFile = editorPsiFile(project, editor) ?: return
    val virtualFile = psiFile.virtualFile ?: return
    val lspClients = findLspClientsForInline(psiFile)
    if (lspClients.isEmpty()) {
      // A client can still be starting when the shortcut fires, so tell the user instead of a silent no-op.
      findPendingLspClientForInline(psiFile)?.let { showLspServerNotReadyHint(editor, it) }
      return
    }
    requestAndApplyCodeActions(
      lspClients, editor, virtualFile,
      kinds = listOf(CodeActionKind.RefactorInline),
      progressTitle = LspBundle.message("lsp.inline.progress.title"),
      noActionsMessage = LspBundle.message("lsp.inline.no.available.actions"),
      popupTitle = LspBundle.message("lsp.inline.popup.title"),
    )
  }

  // The platform hands over the resolved target, which can be a declaration in another file.
  // The code action runs at the caret, so the editor's document owns both the gate and the request.
  private fun editorPsiFile(project: Project, editor: Editor): PsiFile? =
    PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
}

internal fun findLspClientsForInline(psiFile: PsiFile): List<LspClientImpl> {
  val virtualFile = psiFile.virtualFile ?: return emptyList()
  return LspClientManagerImpl.getInstanceImpl(psiFile.project)
    .getClientsWithThisFileOpen(virtualFile)
    .filter { client ->
      client.optsInToInline(psiFile) && client.supportsCodeActionsOfKind(virtualFile, CodeActionKind.RefactorInline)
    }
}

internal fun findPendingLspClientForInline(psiFile: PsiFile): LspClientImpl? =
  findPendingLspClient(psiFile) { it.optsInToInline(psiFile) }

private fun LspClientImpl.optsInToInline(psiFile: PsiFile): Boolean {
  val customizer = descriptor.lspCustomization.codeActionsCustomizer
  return customizer is LspCodeActionsSupport && customizer.inlineRefactoringSupport && customizer.shouldRunRefactoring(psiFile)
}
