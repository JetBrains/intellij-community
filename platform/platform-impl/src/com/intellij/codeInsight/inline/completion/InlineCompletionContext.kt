// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.InlineState.Companion.resetInlineCompletionState
import com.intellij.codeInsight.inline.completion.listeners.InlineCompletionKeyListener
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionUsageTracker
import com.intellij.codeInsight.inline.completion.render.InlineCompletion
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicBoolean

@ApiStatus.Experimental
class InlineCompletionContext private constructor(
  val editor: Editor,
  private val showTracker: InlineCompletionUsageTracker.ShowTracker,
  private var hasPlaceholder: Boolean = false,
) : Disposable {
  private val keyListener = InlineCompletionKeyListener(editor)
  private val isSelecting = AtomicBoolean(false)
  private val inlay = InlineCompletion.forEditor(editor)
  private var inserted = false

  private var element: InlineCompletionElement? = null

  val isCurrentlyDisplayingInlays: Boolean
    get() = !inlay.isEmpty && !hasPlaceholder

  val startOffset: Int?
    get() = inlay.offset

  fun show(element: InlineCompletionElement, offset: Int) {
    LOG.trace("Update inline completion context")
    this.element = element

    if (!inlay.isEmpty) {
      showTracker.rejected()
      inlay.reset()
    }
    if (element.text.isNotBlank() && editor is EditorImpl) {
      inlay.render(element, offset)
      showTracker.shown(editor, element)
      if (!inlay.isEmpty) {
        Disposer.register(this, inlay)
        editor.contentComponent.addKeyListener(keyListener)
      }
    }
  }

  fun insert(): Boolean {
    if (hasPlaceholder) return false
    val offset = inlay.offset ?: return false
    val currentCompletion = element ?: return false

    InlineCompletionHandler.withSafeMute {
      isSelecting.set(true)

      editor.document.insertString(offset, currentCompletion.text)
      editor.caretModel.moveToOffset(offset + currentCompletion.text.length)
      showTracker.accepted()
      inserted = true

      isSelecting.set(false)
      editor.removeInlineCompletionContext()
    }

    LookupManager.getActiveLookup(editor)?.hideLookup(false)
    hide()
    return true
  }

  fun hide() {
    if (!isCurrentlyDisplayingInlays || isSelecting.get()) return

    if (!inlay.isEmpty) {
      if (!inserted) {
        showTracker.rejected()
      }
      Disposer.dispose(inlay)
    }

    editor.contentComponent.removeKeyListener(keyListener)
    editor.resetInlineCompletionState()
  }

  override fun dispose() {
    hide()
  }

  companion object {
    private val LOG = thisLogger()
    private val INLINE_COMPLETION_CONTEXT = Key.create<InlineCompletionContext>("inline.completion.context")

    // Inline context
    internal fun Editor.initOrGetInlineCompletionContext(triggerTracker: InlineCompletionUsageTracker.TriggerTracker): InlineCompletionContext {
      return getUserData(INLINE_COMPLETION_CONTEXT) ?: InlineCompletionContext(this, createShowTracker(triggerTracker)).also {
        LOG.trace("Create new inline completion context")
        putUserData(INLINE_COMPLETION_CONTEXT, it)
      }
    }

    internal fun Editor.initOrGetInlineCompletionContextWithPlaceholder(triggerTracker: InlineCompletionUsageTracker.TriggerTracker): InlineCompletionContext {
      return getUserData(INLINE_COMPLETION_CONTEXT) ?: InlineCompletionContext(
        this, createShowTracker(triggerTracker),
        hasPlaceholder = true
      ).also {
        LOG.trace("Create new inline completion context with placeholder")
        putUserData(INLINE_COMPLETION_CONTEXT, it)
      }
    }

    private fun createShowTracker(triggerTracker: InlineCompletionUsageTracker.TriggerTracker) = InlineCompletionUsageTracker.ShowTracker(
      invocationTime = triggerTracker.invocationTime,
      requestId = triggerTracker.requestId,
    )

    fun Editor.getInlineCompletionContextOrNull(): InlineCompletionContext? = getUserData(INLINE_COMPLETION_CONTEXT)

    fun Editor.removeInlineCompletionContext(): Unit = putUserData(INLINE_COMPLETION_CONTEXT, null).also {
      LOG.trace("Remove inline completion context")
    }

    fun Editor.resetInlineCompletionContext(): Unit? = getInlineCompletionContextOrNull()?.let {
      removeInlineCompletionContext()
      it.hide()
    }

    @Deprecated(
      "Resetting completion context is unsafe now. Use direct get/reset/remove~InlineCompletionContext instead",
      ReplaceWith("getInlineCompletionContextOrNull()"), DeprecationLevel.ERROR
    )
    fun Editor.initOrGetInlineCompletionContext(): InlineCompletionContext {
      return getUserData(INLINE_COMPLETION_CONTEXT)!!
    }
  }
}
