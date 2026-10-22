// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.rendering

import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.impl.DefaultLayeredIconDescriptor
import com.intellij.platform.icons.impl.rendering.layers.CompoundDimensions
import com.intellij.platform.icons.impl.rendering.layers.IconLayerManager
import com.intellij.platform.icons.impl.rendering.layers.IconLayerRenderer
import com.intellij.platform.icons.impl.rendering.layers.maxCompoundSize
import com.intellij.platform.icons.rendering.Dimensions
import com.intellij.platform.icons.rendering.IconRenderer
import com.intellij.platform.icons.rendering.LayerPaintingContext
import com.intellij.platform.icons.rendering.RenderingContext
import com.intellij.platform.icons.rendering.ScalingContext

class DefaultIconRenderer(val iconInstance: DefaultLayeredIconDescriptor, private val context: RenderingContext) : IconRenderer {
    override val iconDescriptor: IconDescriptor = iconInstance
    private var lastThemeDigest: String = context.theme.digest()
    private var layerRenderers = createRenderers()

    private fun createRenderers(): List<IconLayerRenderer> {
        val manager = IconLayerManager.getInstance()
        return iconInstance.layers.map { manager.createRenderer(it, context) }
    }

    override fun render(paintingContext: LayerPaintingContext) {
        val renderers = layers()
        val usedDimensions = usedDimensions(renderers, paintingContext.scaling)
        for (layer in renderers) {
            val nested =
                paintingContext.createNestedLayer(slotWidth = usedDimensions.width, slotHeight = usedDimensions.height)
            layer.render(nested)
        }
    }

  private fun layers(): List<IconLayerRenderer> {
    val themeDigest = context.theme.digest()
    if (lastThemeDigest != themeDigest) {
      layerRenderers = createRenderers()
      lastThemeDigest = themeDigest
    }
    return layerRenderers
  }

    internal fun consumedSpace(): CompoundDimensions = layers().maxCompoundSize { it.layout.consumedSpace() }

    override fun calculateUsedDimensions(scaling: ScalingContext): Dimensions = usedDimensions(layers(), scaling)

    private fun usedDimensions(renderers: List<IconLayerRenderer>, scaling: ScalingContext): Dimensions {
        var width = 0
        var height = 0
        for (layer in renderers) {
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
