// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.thumb

import com.intellij.ide.minimap.geometry.MinimapGeometryData
import com.intellij.util.ui.JBUI
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Graphics2D
import kotlin.math.roundToInt

internal object MinimapThumb {
  fun computeHeight(visibleHeight: Int, contentHeight: Int, minimapHeight: Int): Int {
    if (minimapHeight <= 0) return 0

    val clampedVisibleHeight = visibleHeight.coerceAtLeast(0)
    val scrollRange = (contentHeight - clampedVisibleHeight).coerceAtLeast(0)
    if (scrollRange <= 0) return minimapHeight

    val rawThumbHeight = if (contentHeight > 0) {
      (clampedVisibleHeight * minimapHeight / contentHeight.toDouble()).toInt()
    }
    else {
      0
    }

    val minThumbHeight = minOf(MIN_THUMB_HEIGHT, minimapHeight)
    return rawThumbHeight.coerceIn(minThumbHeight, minimapHeight)
  }

  fun computeStart(scrollOffset: Int, scrollRange: Int, minimapHeight: Int, thumbHeight: Int): Int {
    if (thumbHeight <= 0) return 0

    val clampedScrollRange = scrollRange.coerceAtLeast(0)
    val maxThumbStart = (minimapHeight - thumbHeight).coerceAtLeast(0)
    if (maxThumbStart <= 0 || clampedScrollRange <= 0) return 0

    val clampedScrollOffset = scrollOffset.coerceIn(0, clampedScrollRange)
    return (clampedScrollOffset.toDouble() * maxThumbStart / clampedScrollRange).toInt()
  }

  fun mapThumbStartToScrollOffset(thumbStart: Int, scrollRange: Int, minimapHeight: Int, thumbHeight: Int): Int {
    val clampedScrollRange = scrollRange.coerceAtLeast(0)
    if (clampedScrollRange <= 0) return 0

    val maxThumbStart = (minimapHeight - thumbHeight).coerceAtLeast(0)
    if (maxThumbStart <= 0) return 0

    val clampedThumbStart = thumbStart.coerceIn(0, maxThumbStart)
    return (clampedThumbStart.toDouble() * clampedScrollRange / maxThumbStart).roundToInt()
  }

  fun computeStartFromDrag(y: Int, dragOffset: Int, panelHeight: Int, minimapHeight: Int, thumbHeight: Int): Int {
    val visibleSpan = when {
      minimapHeight <= panelHeight -> minimapHeight - thumbHeight
      else -> panelHeight - thumbHeight
    }.coerceAtLeast(0)
    val desiredTop = (y - dragOffset).coerceIn(0, visibleSpan)

    return when {
      minimapHeight <= thumbHeight -> 0
      minimapHeight <= panelHeight -> desiredTop
      panelHeight <= thumbHeight -> 0
      else -> {
        val maxScroll = (minimapHeight - thumbHeight).toFloat()
        val denominator = (panelHeight - thumbHeight).toFloat()
        (desiredTop * maxScroll / denominator).roundToInt()
      }
    }
  }

  fun paint(graphics: Graphics2D, panelWidth: Int, geometry: MinimapGeometryData, color: Color) {
    if (panelWidth <= 0 || geometry.thumbHeight <= 0) return

    graphics.color = color
    val oldComposite = graphics.composite
    graphics.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, THUMB_ALPHA)
    graphics.fillRect(0, geometry.thumbStart - geometry.areaStart, panelWidth, geometry.thumbHeight)
    graphics.composite = oldComposite
  }

  private val MIN_THUMB_HEIGHT: Int = JBUI.scale(6)  // necessary in case >10 LoC file in fit mode
  private const val THUMB_ALPHA: Float = 0.2f
}
