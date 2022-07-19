// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.runToolbar

import java.awt.Dimension
import java.awt.Point

class RunWidgetResizeController private constructor() : DraggablePane.DragListener {
  companion object {
    private val controller = RunWidgetResizeController()
    fun getInstance(): RunWidgetResizeController {
      return controller
    }
  }

  private var startWidth: Int? = null

  override fun dragStarted(locationOnScreen: Point) {
    startWidth = FixWidthSegmentedActionToolbarComponent.RUN_CONFIG_WIDTH
  }

  override fun dragged(locationOnScreen: Point, offset: Dimension) {
    startWidth?.let {
      FixWidthSegmentedActionToolbarComponent.RUN_CONFIG_WIDTH = it - offset.width
    }
  }

  override fun dragStopped(locationOnScreen: Point, offset: Dimension) {
    dragged(locationOnScreen, offset)
    startWidth = null
  }
}