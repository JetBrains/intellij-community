// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.inline.completion.tooltip

import com.intellij.codeInsight.inline.completion.render.InlineCompletionRenderer
import com.intellij.codeInsight.inline.completion.session.InlineCompletionSession
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseEventArea
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.util.Disposer

internal class InlineCompletionMouseMotionListener : EditorMouseMotionListener {
  private var hoveredSession: InlineCompletionSession? = null

  override fun mouseMoved(e: EditorMouseEvent) {
    if (e.isConsumed) return
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
      enterHover(newHoveredSession)
    }
  }

  private fun enterHover(newHoveredSession: InlineCompletionSession) {
    hoveredSession = newHoveredSession
    Disposer.register(newHoveredSession) {
      hoveredSession = null
    }
    InlineCompletionTooltip.enterHover(newHoveredSession)
  }

  private fun exitHover() {
    hoveredSession = null
  }
}