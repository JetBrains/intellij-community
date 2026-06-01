package com.intellij.platform.lsp.impl.features.intention

import com.intellij.injected.editor.EditorWindow
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.LspBundle
import com.intellij.platform.lsp.api.customization.LspIntentionAction
import com.intellij.platform.lsp.impl.features.quickFix.LspIntentionActionWrapperBase
import com.intellij.psi.PsiFile

internal open class LspIntentionActionWrapper(index: Int) : LspIntentionActionWrapperBase(index) {
  override var lspIntentionAction: LspIntentionAction? = null

  override fun getFamilyName(): String = LspBundle.message("intention.family.name")

  override fun isAvailable(project: Project, editor: Editor, psiFile: PsiFile): Boolean {
    if (editor is EditorWindow) return false

    ensureInitialized(editor)
    return super.isAvailable(project, editor, psiFile)
  }

  private fun ensureInitialized(editor: Editor) {
    val project = editor.project ?: return
    if (project.isDefault) return
    val intentionActions = LspIntentionActionService.getInstance(project).getIntentionActions(editor)
    lspIntentionAction = if (index < intentionActions.size) intentionActions[index] else null
  }
}


internal class LspIntention0 : LspIntentionActionWrapper(0)
internal class LspIntention1 : LspIntentionActionWrapper(1)
internal class LspIntention2 : LspIntentionActionWrapper(2)
internal class LspIntention3 : LspIntentionActionWrapper(3)
internal class LspIntention4 : LspIntentionActionWrapper(4)
internal class LspIntention5 : LspIntentionActionWrapper(5)
internal class LspIntention6 : LspIntentionActionWrapper(6)
internal class LspIntention7 : LspIntentionActionWrapper(7)
internal class LspIntention8 : LspIntentionActionWrapper(8)
internal class LspIntention9 : LspIntentionActionWrapper(9)
internal class LspIntention10 : LspIntentionActionWrapper(10)
internal class LspIntention11 : LspIntentionActionWrapper(11)
internal class LspIntention12 : LspIntentionActionWrapper(12)
internal class LspIntention13 : LspIntentionActionWrapper(13)
internal class LspIntention14 : LspIntentionActionWrapper(14)
