// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.rendering.modifiers

import org.jetbrains.icons.modifiers.MarginIconModifier
import org.jetbrains.icons.rendering.ScalingContext
import org.jetbrains.icons.impl.rendering.layers.LayerLayout

internal fun applyMarginIconModifier(modifier: MarginIconModifier, layout: LayerLayout, scaling: ScalingContext): LayerLayout {
  val leftPx = modifier.left.asPixels(scaling, layout.parentBounds, true)
  val topPx = modifier.top.asPixels(scaling, layout.parentBounds, false)
  val rightPx = modifier.right.asPixels(scaling, layout.parentBounds, true)
  val bottomPx = modifier.bottom.asPixels(scaling, layout.parentBounds, false)

  val finalX = maxOf(layout.layerBounds.x, leftPx)
  val finalY = maxOf(layout.layerBounds.y, topPx)

  return layout.copy(
    layerBounds = layout.layerBounds.copy(
      x = finalX,
      y = finalY,
      width = minOf(layout.parentBounds.width - finalX - rightPx, layout.layerBounds.width),
      height = minOf(layout.parentBounds.height - finalY - bottomPx, layout.layerBounds.height)
    )
  )
}
