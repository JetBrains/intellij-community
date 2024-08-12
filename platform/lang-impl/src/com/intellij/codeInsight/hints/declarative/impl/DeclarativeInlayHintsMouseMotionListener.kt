// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl

import com.intellij.openapi.client.ClientSystemInfo
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseEventArea
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.ui.LightweightHint
import java.awt.Point
import java.awt.event.MouseEvent
import java.lang.ref.WeakReference

class DeclarativeInlayHintsMouseMotionListener : EditorMouseMotionListener {
  private var areaUnderCursor: InlayMouseArea? = null
  private var inlayUnderCursor: WeakReference<Inlay<*>>? = null
  private var ctrlDown = false
  private var hint: LightweightHint? = null

  override fun mouseMoved(e: EditorMouseEvent) {
    val inlay = getInlay(e)
    val renderer = if (inlay == null) null else getRenderer(inlay)
    val mouseArea = if (renderer == null || inlay == null) null else getMouseAreaUnderCursor(inlay, renderer, e.mouseEvent)
    val ctrlDown = isControlDown(e.mouseEvent)

    if (inlay != inlayUnderCursor?.get()) {
      hint?.hide()
      if (renderer != null) {
        hint = renderer.handleHover(e)
      }
      else {
        hint = null
      }
    }

    val hasMovedToAnotherArea = mouseArea != areaUnderCursor
    val hasCtrlKeyStateChanged = ctrlDown != this.ctrlDown
    if (hasMovedToAnotherArea || hasCtrlKeyStateChanged) {
      val oldEntries = areaUnderCursor?.entries
      if (oldEntries != null && hasMovedToAnotherArea) {
        for (entry in oldEntries) {
          entry.isHoveredWithCtrl = false
        }
      }

      val newEntries = mouseArea?.entries
      if (newEntries != null) {
        for (entry in newEntries) {
          entry.isHoveredWithCtrl = ctrlDown
        }
      }

      inlayUnderCursor?.get()?.update()
      inlay?.update()

      areaUnderCursor = mouseArea
      this.ctrlDown = ctrlDown
    }

    if (inlay != inlayUnderCursor?.get()) {
      inlayUnderCursor = inlay?.let { WeakReference(it) }
    }
  }

  private fun isControlDown(e: MouseEvent): Boolean = (ClientSystemInfo.isMac() && e.isMetaDown) || e.isControlDown

  private fun getRenderer(inlay: Inlay<*>): DeclarativeInlayRenderer? {
    val renderer = inlay.renderer
    if (renderer !is DeclarativeInlayRenderer) return null
    return renderer
  }

  private fun getInlay(e: EditorMouseEvent): Inlay<*>? {
    if (e.isConsumed) return null
    if (e.area != EditorMouseEventArea.EDITING_AREA) return null
    return e.inlay
  }

  private fun getMouseAreaUnderCursor(inlay: Inlay<*>, renderer: DeclarativeInlayRenderer, event: MouseEvent): InlayMouseArea? {
    val bounds = inlay.bounds ?: return null
    val inlayPoint = Point(bounds.x, bounds.y)
    val translated = Point(event.x - inlayPoint.x, event.y - inlayPoint.y)

    return renderer.getMouseArea(translated)
  }
}