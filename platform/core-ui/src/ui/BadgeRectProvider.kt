// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import org.jetbrains.annotations.ApiStatus
import java.awt.geom.RoundRectangle2D

@ApiStatus.Internal
@ApiStatus.Experimental
class BadgeRectProvider(
  val arc: Double = 0.5,
  val top: Double = 0.0,
  val left: Double = 0.4,
  val right: Double = 0.0,
  val bottom: Double = 0.7,
  border: Double = getDouble("IconBadge.borderWidth", 1.5) / 20
) : BadgeShapeProvider(border) {
  override fun createShape(width: Int, height: Int, hole: Boolean): RoundRectangle2D? {
    val size = width.coerceAtMost(height)
    if (size <= 0) return null

    val top = height * top

    val left = width * left

    val right = width * right
    if (right <= left) return null

    val bottom = height * bottom
    if (bottom <= top) return null

    val border = when {
      hole -> size * border
      else -> 0.0
    }
    val x = left - border
    val y = top - border
    val w = right + border - x
    val h = bottom + border - y
    val arc = w.coerceAtMost(h) * arc.coerceIn(0.0, 1.0)
    return RoundRectangle2D.Double(x, y, w, h, arc, arc)
  }
}
