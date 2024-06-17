// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.compiler.charts.ui

import java.awt.Point
import javax.swing.JViewport
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class Zoom {
  private var userScale = -1.0
  private var dynamicScale = 24.0

  private var correctionDuration: Long = 0
  private var lastCorrectionTime: Long = System.currentTimeMillis()

  fun toPixels(duration: Long): Double = toPixels(duration.toDouble(), scale())
  fun toPixels(duration: Double): Double = toPixels(duration, scale())
  private fun toPixels(duration: Double, scale: Double): Double = (duration / NANOS) * scale

  fun toDuration(pixels: Int): Long = toDuration(pixels.toDouble(), scale())
  private fun toDuration(pixels: Double, scale: Double): Long = Math.round(pixels / scale * NANOS)

  fun adjustUser(viewport: JViewport, xPosition: Int, delta: Double) {
    if (lastCorrectionTime + 50 < System.currentTimeMillis()) correctionDuration = 0
    val localX = xPosition - viewport.viewPosition.x

    val currentTimeUnderCursor = toDuration(xPosition) + correctionDuration

    val potentialScale = scale() * delta
    val potentialWidth = toPixels(currentTimeUnderCursor.toDouble(), potentialScale) - localX

    userScale = if (potentialWidth < MAX_WIDTH) potentialScale
    else MAX_WIDTH / (currentTimeUnderCursor - localX) * NANOS

    val newViewPositionX = toPixels(currentTimeUnderCursor) - localX
    val correctedViewPositionX = correctedViewPosition(newViewPositionX, localX, currentTimeUnderCursor)

    correctionDuration = currentTimeUnderCursor - toDuration(correctedViewPositionX + localX)
    lastCorrectionTime = System.currentTimeMillis()

    viewport.viewPosition = Point(correctedViewPositionX, viewport.viewPosition.y)
  }

  private fun correctedViewPosition(newPosition: Double, x: Int, duration: Long): Int {
    val correctedX = Math.round(max(0.0, newPosition)).toInt()
    val index = listOf(abs(duration - toDuration(correctedX + x)),
                       abs(duration - toDuration(correctedX + x + 1)),
                       abs(duration - toDuration(correctedX + x - 1)),
                       abs(duration - toDuration(correctedX + x + 2)),
                       abs(duration - toDuration(correctedX + x - 2)))
      .withIndex().minBy { it.value }.index
    return when (index) {
      0 -> return correctedX
      1 -> return correctedX + 1
      2 -> return correctedX - 1
      3 -> return correctedX + 2
      4 -> return correctedX - 2
      else -> correctedX
    }
  }

  private fun scale(): Double = max(MIN_ZOOM_SECONDS, min(MAX_ZOOM_SECONDS, if (userScale == -1.0) dynamicScale else userScale))

  fun adjustDynamic(totalDuration: Int, window: Int) = adjustDynamic(totalDuration.toDouble(), window.toDouble())

  fun adjustDynamic(totalDuration: Double, window: Double) {
    dynamicScale = max(normalize(secondsToScale(totalDuration / NANOS, Math.round(window - 10).toInt())),
                       secondsToScale(60.0, AXIS_DISTANCE_PX))
  }

  private fun secondsToScale(seconds: Double, size: Int): Double = size / seconds
  private fun normalize(scale: Double): Double = max(secondsToScale(MAX_ZOOM_SECONDS, AXIS_DISTANCE_PX),
                                                     min(scale, secondsToScale(MIN_ZOOM_SECONDS, AXIS_DISTANCE_PX)))

  companion object {
    private const val NANOS: Long = 1_000_000_000
  }
}
