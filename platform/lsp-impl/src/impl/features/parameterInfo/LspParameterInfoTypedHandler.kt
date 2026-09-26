package com.intellij.platform.lsp.impl.features.parameterInfo

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.customization.LspSignatureHelpSupport
import com.intellij.platform.lsp.impl.LspClientManagerImpl
import com.intellij.psi.PsiFile

internal class LspParameterInfoTypedHandler : TypedHandlerDelegate(), DumbAware {

  override fun checkAutoPopup(charTyped: Char, project: Project, editor: Editor, file: PsiFile): Result {
    if (project.isDefault) return Result.CONTINUE

    val virtualFile = file.virtualFile?.let { (it as? VirtualFileWindow)?.delegate ?: it } ?: return Result.CONTINUE

    for (client in LspClientManagerImpl.getInstanceImpl(project).getClientsWithThisFileOpen(virtualFile)) {
      val signatureHelpSupport = client.descriptor.lspCustomization.signatureHelpCustomizer as? LspSignatureHelpSupport ?: continue
      if (client.getSignatureHelpTriggerCharacters(file.virtualFile)?.contains(charTyped.toString()) != true) continue
      if (!signatureHelpSupport.isTriggerCharacterRespected(charTyped)) continue

      AutoPopupController.getInstance(project).autoPopupParameterInfo(editor, null)
      return Result.CONTINUE
    }

    return Result.CONTINUE
  }
}