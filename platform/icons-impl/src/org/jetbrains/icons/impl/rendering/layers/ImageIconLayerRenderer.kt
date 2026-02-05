// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.rendering.layers

import org.jetbrains.icons.rendering.Bounds
import org.jetbrains.icons.rendering.Dimensions
import org.jetbrains.icons.rendering.ImageResource
import org.jetbrains.icons.rendering.PaintingApi
import org.jetbrains.icons.rendering.RenderingContext
import org.jetbrains.icons.rendering.ScalingContext
import org.jetbrains.icons.rendering.imageResource
import org.jetbrains.icons.layers.IconLayer
import org.jetbrains.icons.impl.layers.ImageIconLayer
import org.jetbrains.icons.impl.rendering.modifiers.applyTo

class ImageIconLayerRenderer(
  override val layer: ImageIconLayer,
  override val renderingContext: RenderingContext
) : BaseImageIconLayerRenderer() {
  override var image: ImageResource = imageResource(layer.loader, layer.generateImageModifiers(renderingContext))
  override fun calculateExpectedDimensions(scaling: ScalingContext): Dimensions {
    return Dimensions(scaling.applyTo(image.width ?: 16), scaling.applyTo(image.height ?: 16))
  }
}

abstract class BaseImageIconLayerRenderer: IconLayerRenderer {
  abstract var image: ImageResource
  abstract val layer: IconLayer
  protected abstract val renderingContext: RenderingContext

  override fun render(api: PaintingApi) {
    val currentImage = image
    val layout = DefaultLayerLayout(
      Bounds(
        0,
        0,
        api.scaling.applyTo(currentImage.width) ?: api.bounds.width,
        api.scaling.applyTo(currentImage.height) ?: api.bounds.width,
      ),
      api.bounds
    )
    val appliedLayout = layer.modifier.applyTo(layout, api.scaling)
    val finalBounds = appliedLayout.calculateFinalBounds()
    api.drawImage(currentImage, finalBounds.x, finalBounds.y, finalBounds.width, finalBounds.height, alpha = appliedLayout.alpha)
  }
}