// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints.presentation.listeners

import com.intellij.codeInsight.hints.presentation.InputHandler
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseEventArea
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import java.awt.Point

class InlayEditorMouseMotionListener : EditorMouseMotionListener {
  private var activeContainer: InputHandler? = null

  override fun mouseMoved(e: EditorMouseEvent) {
    if (e.isConsumed) return
    val event = e.mouseEvent
    if (e.area != EditorMouseEventArea.EDITING_AREA) {
      exitMouseInActiveContainer()
      activeContainer = null
      return
    }
    val inlay = e.inlay
    val container = inlay?.renderer
    if (activeContainer != container) {
      exitMouseInActiveContainer()
      if (container == null) {
        activeContainer = null
      }
      else if (container is InputHandler) {
        activeContainer = container
      }
    }
    if (container !is InputHandler) return
    val bounds = inlay.bounds ?: return
    val translatedInlayPoint = container.translatePoint(Point(bounds.x, bounds.y))
    val translated = Point(event.x - translatedInlayPoint.x, event.y - translatedInlayPoint.y)
    container.mouseMoved(event, translated)
  }

  private fun exitMouseInActiveContainer() {
    activeContainer?.mouseExited()
  }
}