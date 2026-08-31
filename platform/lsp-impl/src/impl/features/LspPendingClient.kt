package com.intellij.platform.lsp.impl.features

import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.platform.lsp.api.LspBundle
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.LspClientManagerImpl
import com.intellij.psi.PsiFile
import com.intellij.refactoring.util.CommonRefactoringUtil

/**
 * Finds a client that is expected to handle [psiFile] but is still in the [LspServerState.Initializing] state.
 * A refactoring keeps its action enabled for such a client, so the shortcut can report a hint instead of a silent no-op.
 * The client roots must cover the file, so an unrelated client cannot claim it.
 */
internal fun findPendingLspClient(psiFile: PsiFile, optsIn: (LspClientImpl) -> Boolean): LspClientImpl? {
  return LspClientManagerImpl.getInstanceImpl(psiFile.project).getAllClients()
    .find { client ->
      client.state == LspServerState.Initializing &&
      optsIn(client) &&
      client.isExpectedToHandleFile(psiFile)
    }
}

/**
 * True when the client is expected to handle [psiFile] once the file is open:
 * the file is a local content file, the client roots cover it, and the descriptor supports it.
 */
private fun LspClientImpl.isExpectedToHandleFile(psiFile: PsiFile): Boolean {
  val file = psiFile.virtualFile ?: return false
  if (!file.isInLocalFileSystem) return false
  if (!ProjectFileIndex.getInstance(psiFile.project).isInContent(file)) return false
  return descriptor.roots.any { root -> VfsUtilCore.isAncestor(root, file, false) } &&
         descriptor.isSupportedFile(file)
}

/**
 * Shows an editor hint that the server is still starting, so the refactoring cannot run now.
 * In the unit test mode it throws [CommonRefactoringUtil.RefactoringErrorHintException] with the same message,
 * the pattern of [CommonRefactoringUtil.showErrorHint].
 */
internal fun showLspServerNotReadyHint(editor: Editor, lspClient: LspClientImpl) {
  val message = LspBundle.message("lsp.refactoring.server.starting", lspClient.descriptor.presentableName)
  if (ApplicationManager.getApplication().isUnitTestMode) {
    throw CommonRefactoringUtil.RefactoringErrorHintException(message)
  }
  HintManager.getInstance().showErrorHint(
    editor, message,
    editor.caretModel.offset, editor.caretModel.offset,
    HintManager.ABOVE,
    HintManager.HIDE_BY_ANY_KEY or HintManager.HIDE_BY_TEXT_CHANGE or HintManager.UPDATE_BY_SCROLLING,
    3000
  )
}
