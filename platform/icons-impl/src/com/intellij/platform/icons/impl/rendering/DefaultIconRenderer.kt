// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.rendering

import com.intellij.platform.icons.Icon
import com.intellij.platform.icons.impl.DefaultLayeredIcon
import com.intellij.platform.icons.impl.rendering.layers.CompoundDimensions
import com.intellij.platform.icons.impl.rendering.layers.IconLayerManager
import com.intellij.platform.icons.impl.rendering.layers.IconLayerRenderer
import com.intellij.platform.icons.impl.rendering.layers.maxCompoundSize
import com.intellij.platform.icons.rendering.Dimensions
import com.intellij.platform.icons.rendering.IconRenderer
import com.intellij.platform.icons.rendering.LayerPaintingContext
import com.intellij.platform.icons.rendering.RenderingContext
import com.intellij.platform.icons.rendering.ScalingContext

class DefaultIconRenderer(val iconInstance: DefaultLayeredIcon, private val context: RenderingContext) : IconRenderer {
    override val icon: Icon = iconInstance
    private var isLoaded = false
    private val layerRenderers = createRenderers()

    private fun createRenderers(): List<IconLayerRenderer> {
        val manager = IconLayerManager.getInstance()
        val renderers = iconInstance.layers.map { manager.createRenderer(it, context) }
        isLoaded = true
        return renderers
    }

    override fun render(paintingContext: LayerPaintingContext) {
        val usedDimensions = calculateUsedDimensions(paintingContext.scaling)
        for (layer in layerRenderers) {
            val nested =
                paintingContext.createNestedLayer(slotWidth = usedDimensions.width, slotHeight = usedDimensions.height)
            layer.render(nested)
        }
    }

    internal fun consumedSpace(): CompoundDimensions = layerRenderers.maxCompoundSize { it.layout.consumedSpace() }

    override fun calculateUsedDimensions(scaling: ScalingContext): Dimensions {
        var width = 0
        var height = 0
        for (layer in layerRenderers) {
            val dimensions = layer.layout.consumedSpace(scaling)
            if (dimensions.width > width) {
                width = dimensions.width
            }
            if (dimensions.height > height) {
                height = dimensions.height
            }
        }
        return Dimensions(width, height)
    }
}
