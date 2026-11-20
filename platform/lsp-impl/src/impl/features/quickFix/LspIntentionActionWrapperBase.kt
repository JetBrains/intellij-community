package com.intellij.platform.lsp.impl.features.quickFix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.project.DumbAware

/**
 * A base class for the [LspQuickFixWrapper] and the [com.intellij.platform.lsp.impl.features.intention.LspIntentionActionWrapper].
 * This class helps to create an [IntentionAction] object at the moment when the real behavior of this `IntentionAction` is not yet known.
 * The real behavior comes from a [com.intellij.platform.lsp.api.customization.LspIntentionAction], which holds a
 * `CodeAction` object, a response from the LSP server with all the information needed to make the action work.
 *
 * This class is stateless. Each subclass resolves its [com.intellij.platform.lsp.api.customization.LspIntentionAction]
 * and delegates [getText], [isAvailable], [generatePreview] and [invoke] to it.
 */
internal abstract class LspIntentionActionWrapperBase(protected val index: Int) : IntentionAction, Comparable<IntentionAction>, DumbAware {
  /**
   * For performance reasons, this function shouldn't send a request to the LSP server
   */
  override fun getFamilyName(): String = ""

  // Some intention actions must be called without a write action (e.g., they only apply Command)
  override fun startInWriteAction(): Boolean = false

  // Implementing `Comparable` interface is needed to preserve the order as returned by the LSP server
  override fun compareTo(other: IntentionAction): Int = (other as? LspIntentionActionWrapperBase)?.let { index - other.index } ?: 0
}