// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.listeners

import com.intellij.codeInsight.inline.completion.InlineCompletion
import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionUsageTracker.ShownEvents.FinishType
import com.intellij.codeInsight.inline.completion.session.InlineCompletionContext
import com.intellij.codeInsight.inline.completion.utils.InlineCompletionHandlerUtils.hideInlineCompletion
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.ex.FocusChangeListener
import com.intellij.util.application
import com.intellij.util.concurrency.annotations.RequiresEdt

// ML-1086
internal class InlineEditorMouseListener : EditorMouseListener {
  override fun mousePressed(event: EditorMouseEvent) {
    if (InlineCompletionContext.getOrNull(event.editor)?.containsPoint(event) == true) {
      return
    }
    LOG.trace("Valuable mouse pressed event $event")
    hideInlineCompletion(event.editor, FinishType.MOUSE_PRESSED)
  }

  private fun InlineCompletionContext.containsPoint(event: EditorMouseEvent): Boolean {
    val point = event.mouseEvent.point
    return state.elements.any { it.getBounds()?.contains(point) == true }
  }

  companion object {
    private val LOG = thisLogger()
  }
}

/**
 * The listener has two mods:
 * * **Movement is prohibited**: cancels inline completion (via [cancel]) as soon as a caret moves to unexpected position
 * (defined by [completionOffset])
 * * **Adaptive**: [completionOffset] is updated each time a caret offset is changed.
 *
 * In any mode, the inline completion will be canceled if any of the following happens:
 * * Any caret is added/removed
 * * A caret leans to the right, as only left lean is permitted. Otherwise, inlays will be to the left of the caret.
 */
internal abstract class InlineSessionWiseCaretListener : CaretListener {

  protected abstract var completionOffset: Int
    @RequiresEdt
    get
    @RequiresEdt
    set

  protected abstract val mode: Mode
    @RequiresEdt
    get

  @RequiresEdt
  protected abstract fun cancel()

  override fun caretAdded(event: CaretEvent) = cancel()

  override fun caretRemoved(event: CaretEvent) = cancel()

  override fun caretPositionChanged(event: CaretEvent) {
    val newOffset = event.editor.logicalPositionToOffset(event.newPosition)
    when (mode) {
      Mode.ADAPTIVE -> {
        completionOffset = newOffset
      }
      Mode.PROHIBIT_MOVEMENT -> {
        if (event.oldPosition == event.newPosition) {
          // ML-1341
          // It means that we moved caret from the state 'before inline completion' to `after inline completion`
          // In such a case, the actual caret position does not change, only 'leansForward'
          cancel()
        }
        else if (newOffset != completionOffset) {
          cancel()
        }
      }
    }
  }

  protected enum class Mode {
    ADAPTIVE,
    PROHIBIT_MOVEMENT
  }
}

internal class InlineCompletionFocusListener : FocusChangeListener {
  override fun focusGained(editor: Editor) {
    return // IJPL-179647: slow ops because we load a PSI-file on EDT before it's loaded by the platform

    application.invokeLater {
      if (editor.caretModel.caretCount != 1 || editor.caretModel.currentCaret.hasSelection()) {
        return@invokeLater
      }
      val handler = InlineCompletion.getHandlerOrNull(editor) ?: return@invokeLater
      val event = InlineCompletionEvent.EditorFocused(editor)
      handler.invokeEvent(event)
    }
  }

  override fun focusLost(editor: Editor) {
    hideInlineCompletion(editor, FinishType.FOCUS_LOST) // IJPL-186694
  }
}
