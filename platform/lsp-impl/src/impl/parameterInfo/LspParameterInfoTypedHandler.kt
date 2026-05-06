package com.intellij.platform.lsp.impl.parameterInfo

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.customization.LspSignatureHelpSupport
import com.intellij.platform.lsp.impl.LspServerManagerImpl
import com.intellij.psi.PsiFile

internal class LspParameterInfoTypedHandler : TypedHandlerDelegate(), DumbAware {

  override fun checkAutoPopup(charTyped: Char, project: Project, editor: Editor, file: PsiFile): Result {
    if (project.isDefault) return Result.CONTINUE

    val virtualFile = file.virtualFile?.let { (it as? VirtualFileWindow)?.delegate ?: it } ?: return Result.CONTINUE

    for (server in LspServerManagerImpl.getInstanceImpl(project).getServersWithThisFileOpen(virtualFile)) {
      val signatureHelpSupport = server.descriptor.lspCustomization.signatureHelpCustomizer as? LspSignatureHelpSupport ?: continue
      if (server.getSignatureHelpTriggerCharacters(file.virtualFile)?.contains(charTyped.toString()) != true) continue
      if (!signatureHelpSupport.isTriggerCharacterRespected(charTyped)) continue

      AutoPopupController.getInstance(project).autoPopupParameterInfo(editor, null)
      return Result.CONTINUE
    }

    return Result.CONTINUE
  }
}