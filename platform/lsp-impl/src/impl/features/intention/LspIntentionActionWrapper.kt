package com.intellij.platform.lsp.impl.features.intention

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.injected.editor.EditorWindow
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.LspBundle
import com.intellij.platform.lsp.impl.features.quickFix.LspIntentionActionWrapperBase
import com.intellij.psi.PsiFile
import java.lang.ref.WeakReference

/**
 * The platform registers one app-level instance of each subclass. It keeps no resolved action, because such
 * an action holds the project and leaks it when the project closes. [LspIntentionActionService] holds the
 * resolved action at the project level instead.
 *
 * [getText] has no project, so [isAvailable] keeps the project service in [lastService] for [getText] to read.
 * The reference is weak and keeps no project.
 */
internal open class LspIntentionActionWrapper(index: Int) : LspIntentionActionWrapperBase(index) {
  @Volatile
  private var lastService: WeakReference<LspIntentionActionService>? = null

  override fun getFamilyName(): String = LspBundle.message("intention.family.name")

  override fun getText(): String = lastService?.get()?.resolvedIntentionAction(index)?.text ?: ""

  override fun isAvailable(project: Project, editor: Editor, psiFile: PsiFile): Boolean {
    if (editor is EditorWindow) return false
    if (project.isDefault) return false

    val service = LspIntentionActionService.getInstance(project)
    if (lastService?.get() !== service) {
      lastService = WeakReference(service)
    }
    return service.resolveIntentionAction(editor, index)?.isAvailable(project, editor, psiFile) == true
  }

  override fun generatePreview(project: Project, editor: Editor, psiFile: PsiFile): IntentionPreviewInfo =
    LspIntentionActionService.getInstance(project).resolvedIntentionAction(index)?.generatePreview(project, editor, psiFile)
    ?: IntentionPreviewInfo.EMPTY

  override fun invoke(project: Project, editor: Editor, psiFile: PsiFile) {
    LspIntentionActionService.getInstance(project).resolvedIntentionAction(index)?.invoke(project, editor, psiFile)
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
