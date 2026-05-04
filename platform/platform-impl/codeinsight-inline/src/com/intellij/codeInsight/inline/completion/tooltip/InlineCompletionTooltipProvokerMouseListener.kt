// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.tooltip

import com.intellij.codeInsight.inline.completion.session.InlineCompletionSession
import com.intellij.codeInsight.inline.completion.session.containsPoint
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import java.awt.event.MouseEvent

internal class InlineCompletionTooltipProvokerMouseListener : EditorMouseListener {
  override fun mousePressed(event: EditorMouseEvent) {
    if (!isAppropriateRightClick(event)) {
      return
    }
    val session = InlineCompletionSession.getOrNull(event.editor) ?: return

    event.consume()

    if (InlineCompletionTooltip.isShown(session)) {
      InlineCompletionTooltip.hide(session)
    }
    else {
      InlineCompletionTooltip.show(session)
    }
  }

  override fun mouseReleased(event: EditorMouseEvent) {
    if (!isAppropriateRightClick(event)) {
      return
    }
    // suppress context menu popup on Windows/Linux (popup trigger is on release there)
    event.consume()
  }

  private fun isAppropriateRightClick(event: EditorMouseEvent): Boolean {
    val editor = event.editor
    if (event.isConsumed) {
      return false
    }
    if (event.mouseEvent.button != MouseEvent.BUTTON3) { // only a right-click
      return false
    }
    val session = InlineCompletionSession.getOrNull(editor)
    if (session == null || !session.context.isCurrentlyDisplaying()) {
      return false // no inline completion is showing
    }
    if (!session.context.state.containsPoint(event.mouseEvent.point)) {
      return false
    }
    return true
  }
}
