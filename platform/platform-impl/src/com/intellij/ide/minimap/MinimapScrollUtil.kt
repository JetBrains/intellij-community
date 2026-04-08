// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap

import com.intellij.ide.minimap.geometry.MinimapGeometryData
import com.intellij.ide.minimap.thumb.MinimapThumb
import kotlin.math.min
import kotlin.math.roundToInt

internal object MinimapScrollUtil {
  fun targetScrollOffsetForPoint(
    y: Int,
    geometry: MinimapGeometryData,
    contentHeight: Int,
    viewportHeight: Int,
  ): Int? {
    val minimapHeight = geometry.minimapHeight
    if (minimapHeight <= 0 || contentHeight <= 0) return null

    val areaY = (y + geometry.areaStart).coerceIn(0, minimapHeight)
    val percentage = areaY.toDouble() / minimapHeight.toDouble()
    val viewportHalf = viewportHeight / 2

    return (percentage * contentHeight - viewportHalf).roundToInt()
  }

  fun targetScrollOffsetForThumbDrag(
    y: Int,
    dragOffset: Int,
    panelHeight: Int,
    geometry: MinimapGeometryData,
    contentHeight: Int,
    visibleHeight: Int,
  ): Int? {
    val minimapHeight = geometry.minimapHeight
    val thumbHeight = geometry.thumbHeight
    if (panelHeight <= 0 || minimapHeight <= 0 || thumbHeight <= 0 || contentHeight <= 0) return null

    val thumbStart = MinimapThumb.computeStartFromDrag(y, dragOffset, panelHeight, minimapHeight, thumbHeight)
    val effectiveVisibleHeight = min(visibleHeight, contentHeight).coerceAtLeast(0)
    val scrollRange = (contentHeight - effectiveVisibleHeight).coerceAtLeast(0)

    return MinimapThumb.mapThumbStartToScrollOffset(thumbStart, scrollRange, minimapHeight, thumbHeight)
  }
}
