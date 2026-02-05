// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.rendering.layers

import org.jetbrains.icons.design.Circle
import org.jetbrains.icons.design.Rectangle
import org.jetbrains.icons.rendering.Bounds
import org.jetbrains.icons.rendering.Dimensions
import org.jetbrains.icons.rendering.DrawMode
import org.jetbrains.icons.rendering.PaintingApi
import org.jetbrains.icons.rendering.RenderingContext
import org.jetbrains.icons.rendering.ScalingContext
import org.jetbrains.icons.impl.layers.ShapeIconLayer
import org.jetbrains.icons.impl.rendering.modifiers.applyTo
import kotlin.math.roundToInt

class ShapeIconLayerRenderer(
  val layer: ShapeIconLayer,
  val renderingContext: RenderingContext
) : IconLayerRenderer {
  override fun render(api: PaintingApi) {
    val layout = DefaultLayerLayout(
      Bounds(
        0,
        0,
        api.bounds.width,
        api.bounds.height,
      ),
      api.bounds
    )
    val appliedLayout = layer.modifier.applyTo(layout, api.scaling)
    val finalBounds = appliedLayout.calculateFinalBounds()
    val cutoutMargin = appliedLayout.cutoutMargin ?: 0f
    when (layer.shape) {
      Circle -> {
        val radius = finalBounds.width.coerceAtMost(finalBounds.height) / 2.0f
        val roundRadius = radius.roundToInt()
        if (cutoutMargin > 0f) {
          api.drawCircle(
            layer.color,
            finalBounds.x + roundRadius,
            finalBounds.y + roundRadius,
            radius + cutoutMargin + 1,
            1f,
            DrawMode.Clear
          )
        }
        api.drawCircle(layer.color, finalBounds.x + roundRadius, finalBounds.y + roundRadius, radius, 1f)
      }
      Rectangle -> {
        if (cutoutMargin > 0f) {
          val cutoutMargin2 = cutoutMargin + cutoutMargin
          api.drawRect(
            layer.color,
            finalBounds.x - cutoutMargin.roundToInt(),
            finalBounds.y - cutoutMargin.roundToInt(),
            cutoutMargin2.roundToInt(),
            cutoutMargin2.roundToInt(),
            1f,
            DrawMode.Clear
          )
        }
        api.drawRect(layer.color, finalBounds.x, finalBounds.y, finalBounds.width, finalBounds.height, 1f)
      }
    }
  }

  override fun calculateExpectedDimensions(scaling: ScalingContext): Dimensions {
    return Dimensions(0, 0)
  }
}