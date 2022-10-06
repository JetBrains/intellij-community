// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf

import java.awt.Component
import java.awt.event.MouseEvent

internal class MouseDragSelectionEventHandler(private val mouseDraggedOriginal: (MouseEvent) -> Unit) {

  var isNativeSelectionEnabled = false

  fun mouseDragged(e: MouseEvent) {
    when (dragSelectionMode()) {
      DragSelectionMode.ORIGINAL -> mouseDraggedOriginal(e)
      DragSelectionMode.WINDOWS -> mouseDraggedWindows(e)
    }
  }

  private fun dragSelectionMode(): DragSelectionMode =
    when {
      isNativeSelectionEnabled -> DragSelectionMode.WINDOWS
      else -> DragSelectionMode.ORIGINAL
    }

  private enum class DragSelectionMode {
    ORIGINAL,
    WINDOWS
  }

  private fun mouseDraggedWindows(e: MouseEvent) {
    mouseDragged(e, e.x, e.sourceHeight?.div(2) ?: e.y)
  }

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
