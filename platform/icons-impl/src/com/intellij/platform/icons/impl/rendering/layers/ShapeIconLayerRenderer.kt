// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.rendering.layers

import com.intellij.platform.icons.design.Circle
import com.intellij.platform.icons.design.Rectangle
import com.intellij.platform.icons.impl.layers.ShapeIconLayer
import com.intellij.platform.icons.impl.modifiers.asFractionalPixels
import com.intellij.platform.icons.impl.modifiers.asPixels
import com.intellij.platform.icons.rendering.DrawMode
import com.intellij.platform.icons.rendering.LayerPaintingContext
import com.intellij.platform.icons.rendering.RenderingContext
import kotlin.math.roundToInt

class ShapeIconLayerRenderer(val layer: ShapeIconLayer, val renderingContext: RenderingContext) : IconLayerRenderer {
    override val layout: LayerLayout = applyLayout()

    private fun applyLayout(): LayerLayout {
        val dimensions =
            when (layer.shape) {
                is Circle -> {
                    CompoundDimensions((layer.shape.radius * 2).compoundSize(), (layer.shape.radius * 2).compoundSize())
                }
                is Rectangle -> {
                    CompoundDimensions(layer.shape.width.compoundSize(), layer.shape.height.compoundSize())
                }
            }
        return applyLayout(layer.modifier, dimensions.width, dimensions.height)
    }

    override fun render(paintingContext: LayerPaintingContext) {
        val placement = layout.placement(paintingContext)
        val cutoutMargin = layout.cutoutMargin?.asFractionalPixels(paintingContext.scaling) ?: 0f
        when (layer.shape) {
            is Circle -> {
                val radius = layer.shape.radius.asFractionalPixels(paintingContext.scaling)
                val roundRadius = radius.roundToInt()
                if (cutoutMargin != 0f) {
                    paintingContext.drawCircle(
                        layer.color,
                        placement.bounds.x + roundRadius,
                        placement.bounds.y + roundRadius,
                        radius + cutoutMargin.roundToInt(),
                        1f,
                        DrawMode.Clear,
                    )
                }
                paintingContext.drawCircle(
                    layer.color,
                    placement.bounds.x + roundRadius,
                    placement.bounds.y + roundRadius,
                    radius,
                    1f,
                )
            }
            is Rectangle -> {
                val width = layer.shape.width.asPixels(paintingContext.scaling)
                val height = layer.shape.height.asPixels(paintingContext.scaling)
                if (cutoutMargin > 0f) {
                    val cutoutMargin2 = cutoutMargin + cutoutMargin
                    paintingContext.drawRect(
                        layer.color,
                        placement.bounds.x - cutoutMargin.roundToInt(),
                        placement.bounds.y - cutoutMargin.roundToInt(),
                        width + cutoutMargin2.roundToInt(),
                        height + cutoutMargin2.roundToInt(),
                        1f,
                        DrawMode.Clear,
                    )
                }
                paintingContext.drawRect(layer.color, placement.bounds.x, placement.bounds.y, width, height, 1f)
            }
        }
    }
}
