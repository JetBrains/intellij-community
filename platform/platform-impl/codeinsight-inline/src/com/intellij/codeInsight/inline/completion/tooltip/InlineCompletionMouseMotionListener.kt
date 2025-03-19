// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.tooltip

import com.intellij.codeInsight.inline.completion.session.InlineCompletionContext
import com.intellij.codeInsight.inline.completion.session.InlineCompletionSession
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseEventArea
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.util.Disposer
import java.awt.Point

internal class InlineCompletionMouseMotionListener : EditorMouseMotionListener {
  private var hoveredSession: InlineCompletionSession? = null

  override fun mouseMoved(e: EditorMouseEvent) {
    if (e.isConsumed) return
    if (e.area != EditorMouseEventArea.EDITING_AREA) {
      exitHover()
      return
    }
    val newHoveredSession = e.editor.let { InlineCompletionSession.getOrNull(it) }
    if (newHoveredSession == null || !newHoveredSession.context.containsPoint(e.mouseEvent.point)) {
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

  private fun InlineCompletionContext.containsPoint(mousePoint: Point): Boolean {
    return state.elements.mapNotNull { it.getBounds() }.any {
      it.contains(mousePoint)
    }
  }
}