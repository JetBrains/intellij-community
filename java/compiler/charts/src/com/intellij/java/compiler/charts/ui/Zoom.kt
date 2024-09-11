// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.compiler.charts.ui

import java.awt.Point
import java.util.concurrent.TimeUnit
import javax.swing.JViewport
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

class Zoom {
  private var scale = INITIAL_SCALE

  fun toPixels(duration: Long): Double = toPixels(duration.toDouble(), scale)
  fun toPixels(duration: Double): Double = toPixels(duration, scale)
  fun toDuration(pixels: Int): Long = toDuration(pixels.toDouble(), scale)
  fun increase(viewport: JViewport) = adjust(viewport, viewport.getMiddlePoint(), ZOOM_IN_MULTIPLIER)
  fun decrease(viewport: JViewport) = adjust(viewport, viewport.getMiddlePoint(), ZOOM_OUT_MULTIPLIER)

  fun adjust(viewport: JViewport, xPosition: Int, delta: Double): Point {
    val localX = xPosition - viewport.viewPosition.x
    val currentTimeUnderCursor = toDuration(xPosition)
    scale *= delta

    val newViewPositionX = max(toPixels(currentTimeUnderCursor) - localX, 0.0).roundToInt()
    val point = Point(newViewPositionX, viewport.viewPosition.y)
    if (viewport.width < viewport.viewSize.width) {
      viewport.viewPosition = point
    }
    return point
  }

  fun reset(viewport: JViewport) {
    scale = INITIAL_SCALE
    adjust(viewport, viewport.getMiddlePoint(), 1.0)
  }

  private fun JViewport.getMiddlePoint(): Int = viewPosition.x + width / 2
  private fun toPixels(duration: Double, scale: Double): Double = nanosToSeconds(duration * scale)
  private fun toDuration(pixels: Double, scale: Double): Long = secondsToNanos(pixels / scale).roundToLong()
  private fun nanosToSeconds(time: Double): Double = time / TimeUnit.SECONDS.toNanos(1)
  private fun secondsToNanos(time: Double): Double = time * TimeUnit.SECONDS.toNanos(1)

  companion object {
    private const val INITIAL_SCALE: Double = 24.0
    private const val ZOOM_IN_MULTIPLIER: Double = 1.1
    private const val ZOOM_OUT_MULTIPLIER: Double = 0.9
  }
}
