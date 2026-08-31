package com.intellij.platform.lsp.impl.features.extract

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.LspBundle
import com.intellij.platform.lsp.impl.features.codeActions.requestAndApplyCodeActions
import com.intellij.platform.lsp.impl.features.codeActions.showErrorHint
import com.intellij.platform.lsp.impl.features.showLspServerNotReadyHint
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringActionHandler

internal class LspExtractRefactoringHandler(private val kinds: List<String>) : RefactoringActionHandler {

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?, dataContext: DataContext?) {
    if (editor == null || file == null) return
    val virtualFile = file.virtualFile ?: return
    val lspClients = findLspClientsForExtract(file, kinds)
    if (lspClients.isEmpty()) {
      // A client can still be starting when the shortcut fires, so tell the user instead of a silent no-op.
      val pendingClient = findPendingLspClientForExtract(file)
      if (pendingClient != null) showLspServerNotReadyHint(editor, pendingClient)
      else showErrorHint(editor, LspBundle.message("lsp.extract.no.available.actions"))
      return
    }
    requestAndApplyCodeActions(
      lspClients, editor, virtualFile,
      kinds = kinds,
      progressTitle = LspBundle.message("lsp.extract.progress.title"),
      noActionsMessage = LspBundle.message("lsp.extract.no.available.actions"),
      popupTitle = LspBundle.message("lsp.extract.popup.title"),
    )
  }

  override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) {
    // The extract refactorings run from the editor only.
  }
}
