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

internal class RightAdhesionScrollBarListener(
  private val viewport: JViewport,
  private val zoom: Zoom,
  private val shouldScroll: AutoScrollingType
) : AdjustmentListener, MouseWheelListener {
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
      shouldScroll.stop()
      scheduleUpdateShouldScroll()
    } else {
      updateShouldScroll(e.unitsToScroll)
    }
  }

  private fun scheduleUpdateShouldScroll() {
    updateShouldScrollTask?.cancel(false)
    updateShouldScrollTask = executor.schedule(::updateShouldScroll, 100, TimeUnit.MILLISECONDS)
  }

  private fun adjustHorizontalScrollToRightIfNeeded() {
    if (shouldScroll.isActive()) {
      viewport.viewPosition = Point(viewport.viewSize.width - viewport.width, viewport.viewPosition.y)
    }
  }

  private fun updateShouldScroll(additionalValue: Int = 0) {
    if (!shouldScroll.isEnabled()) return
    if (viewport.viewPosition.x + viewport.width + additionalValue >= viewport.viewSize.width)
      shouldScroll.start()
    else
      shouldScroll.stop()
  }

  internal fun scrollToEnd() {
    shouldScroll.start()
    adjustHorizontalScrollToRightIfNeeded()
  }

  private fun disableShouldScroll() {
    shouldScroll.stop()
  }

  fun increase() {
    disableShouldScroll()
    zoom.adjust(viewport, viewport.getMiddlePoint(), ZOOM_IN_MULTIPLIER)
    scheduleUpdateShouldScroll()
  }

  fun decrease() {
    disableShouldScroll()
    zoom.adjust(viewport, viewport.getMiddlePoint(), ZOOM_OUT_MULTIPLIER)
    scheduleUpdateShouldScroll()
  }

  fun reset() {
    zoom.reset(viewport, viewport.getMiddlePoint())
    scheduleUpdateShouldScroll()
  }

  private fun JViewport.getMiddlePoint(): Int = viewPosition.x + width / 2

  companion object {
    private const val ZOOM_IN_MULTIPLIER: Double = 1.1
    private const val ZOOM_OUT_MULTIPLIER: Double = 0.9
  }
}

