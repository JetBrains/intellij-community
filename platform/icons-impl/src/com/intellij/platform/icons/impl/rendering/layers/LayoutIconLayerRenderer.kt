// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.rendering.layers

import com.intellij.platform.icons.impl.layers.LayoutIconLayer
import com.intellij.platform.icons.impl.rendering.DefaultRenderingContext
import com.intellij.platform.icons.rendering.LayerPaintingContext

class LayoutIconLayerRenderer(private val layoutLayer: LayoutIconLayer, renderingContext: DefaultRenderingContext) :
    IconLayerRenderer {
    private val currentContext = renderingContext.adjustTo(layoutLayer)
    private val nestedRenderers = createNestedRenderers(layoutLayer)

    private fun createNestedRenderers(layer: LayoutIconLayer): List<IconLayerRenderer> =
        IconLayerManager.createRenderers(layer.nestedLayers, currentContext)

    override val layout: LayerLayout = applyLayout()

    private fun applyLayout(): LayerLayout =
        when (layoutLayer.direction) {
            LayoutIconLayer.LayoutDirection.Row -> {
                val dimensions = nestedRenderers.compoundWidthSumHeightMax { it.layout.consumedSpace() }
                applyLayout(layoutLayer.modifier, dimensions.width, dimensions.height)
            }
            LayoutIconLayer.LayoutDirection.Column -> {
                val dimensions = nestedRenderers.compoundHeightSumWidthMax { it.layout.consumedSpace() }
                applyLayout(layoutLayer.modifier, dimensions.width, dimensions.height)
            }
            LayoutIconLayer.LayoutDirection.Box -> {
                val dimensions = nestedRenderers.maxCompoundSize { it.layout.consumedSpace() }
                applyLayout(layoutLayer.modifier, dimensions.width, dimensions.height)
            }
        }

    override fun render(paintingContext: LayerPaintingContext) {
        val base = layout.placement(paintingContext)
        when (layoutLayer.direction) {
            LayoutIconLayer.LayoutDirection.Row -> {
                var offset = 0
                for (nestedRenderer in nestedRenderers) {
                    val nestedLayerContext =
                        paintingContext.createNestedLayer(
                            base.bounds.x + offset,
                            base.bounds.y,
                            null,
                            base.bounds.height,
                            scale = base.scale,
                        )
                    nestedRenderer.render(nestedLayerContext)
                    offset += nestedRenderer.layout.consumedSpace(paintingContext.scaling).width
                }
            }
            LayoutIconLayer.LayoutDirection.Column -> {
                var offset = 0
                for (nestedRenderer in nestedRenderers) {
                    val nestedLayerContext =
                        paintingContext.createNestedLayer(
                            base.bounds.x,
                            base.bounds.y + offset,
                            base.bounds.width,
                            scale = base.scale,
                        )
                    nestedRenderer.render(nestedLayerContext)
                    offset += nestedRenderer.layout.consumedSpace(paintingContext.scaling).height
                }
            }
            else -> {
                for (nestedRenderer in nestedRenderers) {
                    val nestedLayerContext =
                        paintingContext.createNestedLayer(
                            base.bounds.x,
                            base.bounds.y,
                            base.bounds.width,
                            base.bounds.height,
                            scale = base.scale,
                        )
                    nestedRenderer.render(nestedLayerContext)
                }
            }
        }
    }
}
