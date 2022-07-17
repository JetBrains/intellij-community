// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.runToolbar

import com.intellij.execution.runToolbar.FixWidthSegmentedActionToolbarComponent.Companion.RUN_CONFIG_SCALED_WIDTH
import com.intellij.execution.runToolbar.FixWidthSegmentedActionToolbarComponent.Companion.RUN_CONFIG_WIDTH_PROP
import com.intellij.execution.runToolbar.FixWidthSegmentedActionToolbarComponent.Companion.RUN_CONFIG_WIDTH_UNSCALED_MIN
import com.intellij.ide.util.PropertiesComponent
import com.intellij.util.ui.JBUI
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
    startWidth = RUN_CONFIG_SCALED_WIDTH
  }

  override fun dragged(locationOnScreen: Point, offset: Dimension) {
    startWidth?.let {
      RUN_CONFIG_SCALED_WIDTH = it - offset.width
      PropertiesComponent.getInstance().setValue(RUN_CONFIG_WIDTH_PROP, RUN_CONFIG_SCALED_WIDTH, JBUI.scale(RUN_CONFIG_WIDTH_UNSCALED_MIN))
    }
  }

  override fun dragStopped(locationOnScreen: Point, offset: Dimension) {
    dragged(locationOnScreen, offset)
    startWidth = null
  }
}