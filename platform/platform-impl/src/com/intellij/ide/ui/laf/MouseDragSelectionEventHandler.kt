// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf

import com.intellij.openapi.util.SystemInfo
import java.awt.Component
import java.awt.event.MouseEvent

internal class MouseDragSelectionEventHandler(private val mouseDraggedOriginal: (MouseEvent) -> Unit) {

  var isNativeSelectionEnabled = false

  fun mouseDragged(e: MouseEvent) {
    when (dragSelectionMode()) {
      DragSelectionMode.ORIGINAL -> mouseDraggedOriginal(e)
      DragSelectionMode.WINDOWS -> mouseDraggedWindows(e)
      DragSelectionMode.UNIX -> mouseDraggedUnix(e)
    }
  }

  private fun dragSelectionMode(): DragSelectionMode =
    when {
      !isNativeSelectionEnabled -> DragSelectionMode.ORIGINAL
      SystemInfo.isWindows -> DragSelectionMode.WINDOWS
      else -> DragSelectionMode.UNIX
    }

  private enum class DragSelectionMode {
    ORIGINAL,
    WINDOWS,
    UNIX
  }

  private fun mouseDraggedWindows(e: MouseEvent) {
    // Pretend the mouse always moves horizontally,
    // as this is what Windows users generally expect.
    mouseDragged(e, e.x, e.sourceHeight?.div(2) ?: e.y)
  }

  private fun mouseDraggedUnix(e: MouseEvent) {
    val height = e.sourceHeight
    val width = e.sourceWidth
    if (height == null || width == null) { // A next-to-impossible scenario, fall back to original behavior.
      mouseDraggedOriginal(e)
      return
    }
    // In case of the mouse cursor moved above or below the component,
    // imitate fast selection by moving the mouse cursor far away horizontally either left or right.
    val normalizedY = height / 2
    when {
      e.y < 0 -> mouseDragged(e, -OMG_ITS_OVER_9000, normalizedY)
      e.y > height -> mouseDragged(e, width + OMG_ITS_OVER_9000, normalizedY)
      else -> mouseDragged(e, e.x, e.y)
    }
  }

  private val MouseEvent.sourceWidth: Int? get() = (source as? Component)?.width
  private val MouseEvent.sourceHeight: Int? get() = (source as? Component)?.height

  private fun mouseDragged(e: MouseEvent, x: Int, y: Int) {
    val originalX = e.x
    val originalY = e.y
    e.translatePoint(x - originalX, y - originalY)
    try {
      mouseDraggedOriginal(e)
    }
    finally {
      e.translatePoint(originalX - x, originalY - y)
    }
  }

}

private const val OMG_ITS_OVER_9000 = 9999
