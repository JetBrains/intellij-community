// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.compiler.charts.ui

import com.intellij.util.concurrency.AppExecutorUtil
import java.awt.Point
import java.awt.event.AdjustmentEvent
import java.awt.event.AdjustmentListener
import java.awt.event.MouseWheelEvent
import java.awt.event.MouseWheelListener
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.swing.JViewport

internal class RightAdhesionScrollBarListener(private val viewport: JViewport) : AdjustmentListener, MouseWheelListener {
  private var shouldScroll = true
  private val executor = AppExecutorUtil.createBoundedScheduledExecutorService("Compilation charts adjust value listener", 1)
  private var updateShouldScrollTask: ScheduledFuture<*>? = null
  override fun adjustmentValueChanged(e: AdjustmentEvent) {
    if (e.valueIsAdjusting) {
      updateShouldScroll()
    }
    adjustHorizontalScrollToRightIfNeeded()
  }

  override fun mouseWheelMoved(e: MouseWheelEvent) {
    if (e.isControlDown) {
      shouldScroll = false
      scheduleUpdateShouldScroll()
    } else {
      updateShouldScroll(e.unitsToScroll)
    }
  }

  internal fun scheduleUpdateShouldScroll() {
    updateShouldScrollTask?.cancel(false)
    updateShouldScrollTask = executor.schedule(::updateShouldScroll, 100, TimeUnit.MILLISECONDS)
  }

  private fun adjustHorizontalScrollToRightIfNeeded() {
    if (shouldScroll) {
      viewport.viewPosition = Point(viewport.viewSize.width - viewport.width, viewport.viewPosition.y)
    }
  }

  private fun updateShouldScroll(additionalValue: Int = 0) {
    shouldScroll = viewport.viewPosition.x + viewport.width + additionalValue >= viewport.viewSize.width
  }

  internal fun scrollToEnd() {
    shouldScroll = true
    adjustHorizontalScrollToRightIfNeeded()
  }

  internal fun disableShouldScroll() {
    shouldScroll = false
  }
}