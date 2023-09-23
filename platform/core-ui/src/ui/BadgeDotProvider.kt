// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import org.jetbrains.annotations.ApiStatus
import java.awt.geom.Ellipse2D

@ApiStatus.Internal
@ApiStatus.Experimental
class BadgeDotProvider(
  val x: Double = getDouble("IconBadge.dotX", 16.5) / 20,
  val y: Double = getDouble("IconBadge.dotY", 3.5) / 20,
  val radius: Double = getDouble("IconBadge.dotRadius", 3.5) / 20,
  border: Double = getDouble("IconBadge.borderWidth", 1.5) / 20
) : BadgeShapeProvider(border) {
  override fun createShape(width: Int, height: Int, hole: Boolean): Ellipse2D? {
    val size = width.coerceAtMost(height)
    if (size <= 0) return null

    val radius = size * radius
    if (radius <= 0) return null

    val x = width * x
    if (0 > x + radius || x - radius > width) return null

    val y = height * y
    if (0 > y + radius || y - radius > height) return null

    val border = when {
      hole -> size * border
      else -> 0.0
    }
    val r = radius + border.coerceAtLeast(0.0)
    return Ellipse2D.Double(x - r, y - r, r + r, r + r)
  }
}
