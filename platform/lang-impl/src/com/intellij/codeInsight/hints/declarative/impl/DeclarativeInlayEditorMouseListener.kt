// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl

import com.intellij.codeInsight.hints.declarative.impl.inlayRenderer.DeclarativeInlayRendererBase
import com.intellij.openapi.client.ClientSystemInfo
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseEventArea
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.ex.EditorEx
import org.jetbrains.annotations.ApiStatus
import java.awt.Point
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities

@ApiStatus.Internal
class DeclarativeInlayEditorMouseListener : EditorMouseListener {
  override fun mouseClicked(e: EditorMouseEvent) {
    if (e.isConsumed) return
    val event = e.mouseEvent
    if (e.area != EditorMouseEventArea.EDITING_AREA) return
    val inlay = e.inlay ?: return
    val renderer = inlay.renderer
    if (renderer !is DeclarativeInlayRendererBase<*>) return
    val bounds = inlay.bounds ?: return
    val inlayPoint = Point(bounds.x, bounds.y)
    val translated = Point(event.x - inlayPoint.x, event.y - inlayPoint.y)
    if (SwingUtilities.isRightMouseButton(event) && !SwingUtilities.isLeftMouseButton(event)) {
      renderer.handleRightClick(e, translated)
      return
    }
    val controlDown = isControlDown(event)
    renderer.handleLeftClick(e, translated, controlDown)
    inlay.update()
  }

  override fun mouseReleased(e: EditorMouseEvent) {
    (e.editor as? EditorEx)?.setCustomCursor(DeclarativeInlayHintsMouseMotionListener::class.java, null)
  }

  private fun isControlDown(e: MouseEvent): Boolean = (ClientSystemInfo.isMac() && e.isMetaDown) || e.isControlDown
}