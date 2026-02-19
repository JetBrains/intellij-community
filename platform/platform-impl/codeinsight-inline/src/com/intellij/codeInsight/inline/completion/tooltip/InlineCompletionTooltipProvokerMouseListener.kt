// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.tooltip

import com.intellij.codeInsight.inline.completion.session.InlineCompletionSession
import com.intellij.codeInsight.inline.completion.session.containsPoint
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import java.awt.event.MouseEvent

internal class InlineCompletionTooltipProvokerMouseListener : EditorMouseListener {
  override fun mousePressed(event: EditorMouseEvent) {
    val editor = event.editor
    if (event.isConsumed) {
      return
    }
    if (event.mouseEvent.button != MouseEvent.BUTTON3) { // only a right-click
      return
    }
    val session = InlineCompletionSession.getOrNull(editor)
    if (session == null || !session.context.isCurrentlyDisplaying()) {
      return // no inline completion is showing
    }
    if (!session.context.state.containsPoint(event.mouseEvent.point)) {
      return
    }

    event.consume()
    InlineCompletionTooltip.show(session)
  }
}
