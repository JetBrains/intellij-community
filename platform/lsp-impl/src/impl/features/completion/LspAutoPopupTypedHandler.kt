package com.intellij.platform.lsp.impl.features.completion

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.customization.LspCompletionSupport
import com.intellij.platform.lsp.impl.LspClientManagerImpl
import com.intellij.psi.PsiFile

/**
 * Triggers code completion autopopup if the character typed in the editor is listed in the LSP server capabilities
 * as one of the characters that should trigger code completion
 * (see [CompletionOptions.triggerCharacters](https://microsoft.github.io/language-server-protocol/specification/#completionOptions))
 */
internal class LspAutoPopupTypedHandler : TypedHandlerDelegate() {
  override fun checkAutoPopup(charTyped: Char, project: Project, editor: Editor, psiFile: PsiFile): Result {
    if (project.isDefault) return Result.CONTINUE

    val file = psiFile.getOriginalFile().getVirtualFile()?.let { (it as? VirtualFileWindow)?.delegate ?: it }
               ?: return Result.CONTINUE

    for (client in LspClientManagerImpl.getInstanceImpl(project).getClientsWithThisFileOpen(file)) {
      if (client.serverCapabilities?.completionProvider?.triggerCharacters?.contains(charTyped.toString()) != true) continue
      val completionSupport = client.descriptor.lspCustomization.completionCustomizer as? LspCompletionSupport ?: continue
      if (!completionSupport.isTriggerCharacterRespected(charTyped)) continue

      AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
      return Result.STOP
    }

    return Result.CONTINUE
  }
}
