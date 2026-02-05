// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.rendering.layers

import org.jetbrains.icons.rendering.Bounds
import org.jetbrains.icons.rendering.Dimensions
import org.jetbrains.icons.rendering.PaintingApi
import org.jetbrains.icons.rendering.RenderingContext
import org.jetbrains.icons.rendering.ScalingContext
import org.jetbrains.icons.rendering.createRenderer
import org.jetbrains.icons.impl.layers.IconIconLayer
import org.jetbrains.icons.impl.rendering.modifiers.applyTo

class IconIconLayerRenderer(
  private val layer: IconIconLayer,
  private val renderingContext: RenderingContext
) : IconLayerRenderer {
  private val renderer = layer.icon.createRenderer(renderingContext.adjustTo(layer))

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
    val boundApi = api.withCustomContext(appliedLayout.calculateFinalBounds())
    renderer.render(boundApi)
  }

  override fun calculateExpectedDimensions(scaling: ScalingContext): Dimensions {
    return renderer.calculateExpectedDimensions(scaling)
  }
}