package com.intellij.platform.lsp.impl.features.quickFix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.customization.LspIntentionAction
import com.intellij.psi.PsiFile
import org.eclipse.lsp4j.CodeAction

/**
 * A base class for the [LspQuickFixWrapper] and the [com.intellij.platform.lsp.impl.features.intention.LspIntentionActionWrapper].
 * This class helps to create an [IntentionAction] object at the moment when the real behavior of this `IntentionAction` is not yet known.
 * The real behavior becomes known later, when the [lspIntentionAction] property is initialized.
 * [LspIntentionAction] object holds the [CodeAction] object, which is a response from the LSP server that contains all the information
 * needed to make this class actionable.
 */
internal abstract class LspIntentionActionWrapperBase(protected val index: Int) : IntentionAction, Comparable<IntentionAction>, DumbAware {
  abstract var lspIntentionAction: LspIntentionAction?

  /**
   * For performance reasons, this function shouldn't send a request to the LSP server
   */
  override fun getFamilyName(): String = ""

  // Some intention actions must be called without a write action (e.g., they only apply Command)
  override fun startInWriteAction(): Boolean = false

  // Implementing `Comparable` interface is needed to preserve the order as returned by the LSP server
  override fun compareTo(other: IntentionAction): Int = (other as? LspIntentionActionWrapperBase)?.let { index - other.index } ?: 0


  override fun getText(): String = lspIntentionAction?.text ?: ""

  override fun isAvailable(project: Project, editor: Editor, psiFile: PsiFile): Boolean =
    lspIntentionAction?.isAvailable(project, editor, psiFile) == true

  override fun generatePreview(project: Project, editor: Editor, psiFile: PsiFile): IntentionPreviewInfo =
    lspIntentionAction?.generatePreview(project, editor, psiFile) ?: IntentionPreviewInfo.EMPTY

  override fun invoke(project: Project, editor: Editor, psiFile: PsiFile) {
    lspIntentionAction?.invoke(project, editor, psiFile)
  }
}