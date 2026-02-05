// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.rendering.layers

import com.intellij.platform.icons.design.dp
import com.intellij.platform.icons.impl.layers.ImageIconLayer
import com.intellij.platform.icons.impl.rendering.DefaultRenderingContext
import com.intellij.platform.icons.layers.IconLayer
import com.intellij.platform.icons.rendering.ImageResource
import com.intellij.platform.icons.rendering.LayerPaintingContext

class ImageIconLayerRenderer(
    override val layer: ImageIconLayer,
    override val renderingContext: DefaultRenderingContext,
) : BaseImageIconLayerRenderer() {
    override var image: ImageResource =
        renderingContext.imageResource(layer.loader, layer.generateImageModifiers(renderingContext))
    override var layout: LayerLayout = applyLayout(image)
}

abstract class BaseImageIconLayerRenderer : IconLayerRenderer {
    abstract var image: ImageResource
    abstract val layer: IconLayer
    protected abstract val renderingContext: DefaultRenderingContext

    protected fun applyLayout(currentImage: ImageResource): LayerLayout {
        val w = (currentImage.width ?: 16).dp
        val h = (currentImage.height ?: 16).dp
        return applyLayout(layer.modifier, w.compoundSize(), h.compoundSize())
    }

    override fun render(paintingContext: LayerPaintingContext) {
        val currentImage = image
        val placement = layout.placement(paintingContext)
        paintingContext.drawImage(
            currentImage,
            placement.bounds.x,
            placement.bounds.y,
            placement.bounds.width,
            placement.bounds.height,
            alpha = layout.alpha,
        )
    }
}
