// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.compiler.charts.ui

import java.awt.event.AdjustmentEvent
import java.awt.event.AdjustmentListener
import java.awt.event.MouseWheelEvent
import java.awt.event.MouseWheelListener
import javax.swing.JScrollBar

internal class RightAdhesionScrollBarListener(private val horizontalScrollBar: JScrollBar) : AdjustmentListener, MouseWheelListener {
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
      horizontalScrollBar.value = horizontalScrollBar.maximum - horizontalScrollBar.minimum
    }
  }

  private fun updateShouldScroll(additionalValue: Int = 0) {
    shouldScroll = horizontalScrollBar.value + horizontalScrollBar.width + additionalValue >= horizontalScrollBar.maximum
  }
}