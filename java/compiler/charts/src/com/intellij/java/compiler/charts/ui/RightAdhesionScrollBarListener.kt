// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.compiler.charts.ui

import java.awt.Point
import java.awt.event.AdjustmentEvent
import java.awt.event.AdjustmentListener
import java.awt.event.MouseWheelEvent
import java.awt.event.MouseWheelListener
import javax.swing.JViewport

internal class RightAdhesionScrollBarListener(private val viewport: JViewport) : AdjustmentListener, MouseWheelListener {
  private var shouldScroll = true

  override fun adjustmentValueChanged(e: AdjustmentEvent) {
    if (e.valueIsAdjusting) {
      updateShouldScroll()
    }
    adjustHorizontalScrollToRightIfNeeded()
  }

  override fun mouseWheelMoved(e: MouseWheelEvent) {
    updateShouldScroll(e.unitsToScroll)
  }

  private fun adjustHorizontalScrollToRightIfNeeded() {
    if (shouldScroll) {
      viewport.viewPosition = Point(viewport.viewSize.width - viewport.width, viewport.viewPosition.y)
    }
  }

  private fun updateShouldScroll(additionalValue: Int = 0) {
    shouldScroll = viewport.viewPosition.x + viewport.width + additionalValue >= viewport.viewSize.width
  }
}