// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.inline.completion.tooltip

import com.intellij.codeInsight.inline.completion.render.InlineCompletionRenderer
import com.intellij.codeInsight.inline.completion.session.InlineCompletionSession
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseEventArea
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import java.awt.Point

internal class InlineCompletionMouseMotionListener : EditorMouseMotionListener {
  private var hoveredSession: InlineCompletionSession? = null

  override fun mouseMoved(e: EditorMouseEvent) {
    if (e.isConsumed) return
    val event = e.mouseEvent
    if (e.area != EditorMouseEventArea.EDITING_AREA) {
      exitHover()
      return
    }
    if (e.inlay?.renderer !is InlineCompletionRenderer) {
      exitHover()
      return
    }
    val newHoveredSession = e.inlay?.editor?.let { InlineCompletionSession.getOrNull(it) }
    if (newHoveredSession == null) {
      exitHover()
      return
    }
    if (hoveredSession !== newHoveredSession) {
      exitHover()
      enterHover(newHoveredSession, event.locationOnScreen)
    }
  }

  private fun enterHover(newHoveredSession: InlineCompletionSession, locationOnScreen: Point) {
    hoveredSession = newHoveredSession
    InlineCompletionTooltip.enterHover(newHoveredSession, locationOnScreen)
  }

  private fun exitHover() {
    hoveredSession?.let {
      hoveredSession = null
    }
  }
}