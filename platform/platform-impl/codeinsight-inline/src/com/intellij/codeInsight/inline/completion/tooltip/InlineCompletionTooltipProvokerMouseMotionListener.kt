// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.tooltip

import com.intellij.codeInsight.inline.completion.editor.InlineCompletionEditorType
import com.intellij.codeInsight.inline.completion.session.InlineCompletionSession
import com.intellij.codeInsight.inline.completion.session.containsPoint
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseEventArea
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.util.Disposer

internal class InlineCompletionTooltipProvokerMouseMotionListener : EditorMouseMotionListener {
  private var hoveredSession: InlineCompletionSession? = null

  override fun mouseMoved(e: EditorMouseEvent) {
    if (e.isConsumed || !isEditorTypeSupported(e.editor)) {
      return
    }

    if (e.area != EditorMouseEventArea.EDITING_AREA) {
      exitHover()
      return
    }
    val newHoveredSession = e.editor.let { InlineCompletionSession.getOrNull(it) }
    if (newHoveredSession == null || !newHoveredSession.context.state.containsPoint(e.mouseEvent.point)) {
      exitHover()
      return
    }
    if (hoveredSession !== newHoveredSession) {
      exitHover()
      enterHover(newHoveredSession)
    }
  }

  private fun enterHover(newHoveredSession: InlineCompletionSession) {
    hoveredSession = newHoveredSession
    Disposer.register(newHoveredSession) {
      exitHover()
    }
    InlineCompletionTooltip.show(newHoveredSession)
  }

  private fun exitHover() {
    hoveredSession = null
  }

  private fun isEditorTypeSupported(editor: Editor): Boolean {
    return when (InlineCompletionEditorType.get(editor)) {
      InlineCompletionEditorType.TERMINAL,
      InlineCompletionEditorType.MAIN_EDITOR,
        -> true
      InlineCompletionEditorType.XDEBUGGER,
      InlineCompletionEditorType.COMMIT_MESSAGES,
      InlineCompletionEditorType.AI_ASSISTANT_CHAT_INPUT,
      InlineCompletionEditorType.UNKNOWN,
        -> false
    }
  }
}