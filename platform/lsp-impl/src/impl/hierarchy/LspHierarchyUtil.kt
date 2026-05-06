package com.intellij.platform.lsp.impl.hierarchy

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.impl.LspServerImpl
import com.intellij.platform.lsp.impl.LspServerManagerImpl
import com.intellij.platform.lsp.util.getLsp4jPosition
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement

internal fun getTargetFromEditor(
  dataContext: DataContext,
  serverKey: Key<LspServerImpl>,
  serverPredicate: (server: LspServerImpl, file: VirtualFile) -> Boolean,
): PsiElement? {
  val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return null
  val editor = CommonDataKeys.EDITOR.getData(dataContext) ?: return null
  val caretOffset = editor.caretModel.offset
  val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return null

  val server = findSupportingServer(project, psiFile.viewProvider.virtualFile, serverPredicate) ?: return null
  val position = getLsp4jPosition(editor.document, caretOffset)

  return LspFakePsiElement(psiFile = psiFile, lsp4jPosition = position)
    .apply { putUserData(serverKey, server) }
}

internal fun findSupportingServer(
  project: Project,
  file: VirtualFile,
  serverPredicate: (LspServerImpl, VirtualFile) -> Boolean,
): LspServerImpl? =
  LspServerManagerImpl.getInstanceImpl(project)
    .getAllRunningServers()
    .filter { it.isSupportedFile(file) }
    .firstOrNull { serverPredicate(it, file) }