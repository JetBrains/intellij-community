package com.intellij.platform.lsp.impl.features.hierarchy

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.LspClientManagerImpl
import com.intellij.platform.lsp.util.getLsp4jPosition
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement

internal fun getTargetFromEditor(
  dataContext: DataContext,
  clientKey: Key<LspClientImpl>,
  clientPredicate: (client: LspClientImpl, file: VirtualFile) -> Boolean,
): PsiElement? {
  val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return null
  val editor = CommonDataKeys.EDITOR.getData(dataContext) ?: return null
  val caretOffset = editor.caretModel.offset
  val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return null

  val client = findSupportingClient(project, psiFile.viewProvider.virtualFile, clientPredicate) ?: return null
  val position = getLsp4jPosition(editor.document, caretOffset)

  return LspFakePsiElement(psiFile = psiFile, lsp4jPosition = position)
    .apply { putUserData(clientKey, client) }
}

internal fun findSupportingClient(
  project: Project,
  file: VirtualFile,
  clientPredicate: (LspClientImpl, VirtualFile) -> Boolean,
): LspClientImpl? =
  LspClientManagerImpl.getInstanceImpl(project)
    .getRunningClients()
    .filter { it.isSupportedFile(file) }
    .firstOrNull { clientPredicate(it, file) }