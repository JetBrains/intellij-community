// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints.presentation.listeners

import com.intellij.codeInsight.hints.presentation.InputHandler
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseEventArea
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.util.SlowOperations
import java.awt.Point
import java.awt.event.MouseEvent

/**
 * Global mouse listener, that provide events to inlay hints at mouse coordinates.
 */
class InlayEditorMouseListener : EditorMouseListener {

  override fun mouseClicked(e: EditorMouseEvent) {
    handleMouseAction(e) { event, translated ->
      mouseClicked(event, translated)
    }
  }

  override fun mousePressed(e: EditorMouseEvent) {
    handleMouseAction(e) { event, translated ->
      mousePressed(event, translated)
    }
  }

  override fun mouseReleased(e: EditorMouseEvent) {
    handleMouseAction(e) { event, translated ->
      mouseReleased(event, translated)
    }
  }

  private fun handleMouseAction(e: EditorMouseEvent, handler: InputHandler.(MouseEvent, Point) -> Unit) {
    if (e.isConsumed) return
    val event = e.mouseEvent
    if (e.area != EditorMouseEventArea.EDITING_AREA) return
    val inlay = e.inlay ?: return
    val renderer = inlay.renderer
    if (renderer !is InputHandler) return
    val bounds = inlay.bounds ?: return
    val inlayPoint = Point(bounds.x, bounds.y)
    val translated = Point(event.x - inlayPoint.x, event.y - inlayPoint.y)
    SlowOperations.startSection(SlowOperations.ACTION_PERFORM).use {
      handler(renderer, event, translated)
    }
  }
}