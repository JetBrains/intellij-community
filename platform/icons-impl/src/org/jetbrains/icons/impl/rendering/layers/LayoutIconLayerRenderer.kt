// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.rendering.layers

import org.jetbrains.icons.rendering.Bounds
import org.jetbrains.icons.rendering.Dimensions
import org.jetbrains.icons.rendering.PaintingApi
import org.jetbrains.icons.rendering.RenderingContext
import org.jetbrains.icons.rendering.ScalingContext
import org.jetbrains.icons.impl.layers.IconLayerManager
import org.jetbrains.icons.impl.layers.LayoutIconLayer
import org.jetbrains.icons.impl.rendering.modifiers.applyTo
import org.jetbrains.icons.impl.rendering.modifiers.asPixels

class LayoutIconLayerRenderer(
  private val layoutLayer: LayoutIconLayer,
  private val renderingContext: RenderingContext,
) : IconLayerRenderer {
  private val currentContext = renderingContext.adjustTo(layoutLayer)
  private val nestedRenderers = createNestedRenderers(layoutLayer)

  private fun createNestedRenderers(layer: LayoutIconLayer): List<IconLayerRenderer> {
    return IconLayerManager.createRenderers(layer.nestedLayers, currentContext)
  }

  override fun render(api: PaintingApi) {
    val layout = DefaultLayerLayout(
      Bounds(
        0,
        0,
        api.bounds.width,
        api.bounds.height,
      ),
      api.bounds,
    )
    val appliedLayout = layoutLayer.modifier.applyTo(layout, api.scaling)
    val finalBounds = appliedLayout.calculateFinalBounds()

    if (layoutLayer.direction == LayoutIconLayer.LayoutDirection.Row) {
      val spacingPx = layoutLayer.spacing.asPixels(api.scaling, api.bounds, true)
      val remainingSize = finalBounds.width - spacingPx * (nestedRenderers.count() - 1)
      val size = remainingSize / nestedRenderers.count()
      var offset = 0
      for (nestedRenderer in nestedRenderers) {
        val nestedApi = api.withCustomContext(Bounds(finalBounds.x + offset, finalBounds.y, size, finalBounds.height))
        nestedRenderer.render(nestedApi)
        offset += size + spacingPx
      }
    } else {
      val spacingPx = layoutLayer.spacing.asPixels(api.scaling, api.bounds, false)
      val remainingSize = finalBounds.height - spacingPx * (nestedRenderers.count() - 1)
      val size = remainingSize / nestedRenderers.count()
      var offset = 0
      for (nestedRenderer in nestedRenderers) {
        val nestedApi = api.withCustomContext(Bounds(finalBounds.x, finalBounds.y + offset, finalBounds.width, size))
        nestedRenderer.render(nestedApi)
        offset += size + spacingPx
      }
    }
  }

  override fun calculateExpectedDimensions(scaling: ScalingContext): Dimensions {
    var width = 0
    var height = 0
    for (layer in nestedRenderers) {
      val dimensions = layer.calculateExpectedDimensions(scaling)
      if (layoutLayer.direction == LayoutIconLayer.LayoutDirection.Row) {
        width += dimensions.width
        if (dimensions.height > height) {
          height = dimensions.height
        }
      } else {
        height += dimensions.height
        if (dimensions.width > width) {
          width = dimensions.width
        }
      }
    }
    return Dimensions(width, height)
  }
}