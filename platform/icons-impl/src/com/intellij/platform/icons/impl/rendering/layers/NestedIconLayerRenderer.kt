// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.rendering.layers

import com.intellij.platform.icons.impl.layers.NestedIconLayer
import com.intellij.platform.icons.design.dp
import com.intellij.platform.icons.impl.rendering.DefaultDeferredIconRenderer
import com.intellij.platform.icons.impl.rendering.DefaultIconRenderer
import com.intellij.platform.icons.impl.rendering.DefaultRenderingContext
import com.intellij.platform.icons.rendering.IconRenderer
import com.intellij.platform.icons.rendering.LayerPaintingContext
import com.intellij.platform.icons.rendering.createRenderer

class NestedIconLayerRenderer(private val layer: NestedIconLayer, renderingContext: DefaultRenderingContext) :
    IconLayerRenderer {
    private val renderer = layer.iconDescriptor.createRenderer(renderingContext.adjustTo(layer))

    /** The layers that [cachedLayout] was computed from, see [layoutSource]. */
    private var cachedLayoutSource: Any? = null
    private var cachedLayout: LayerLayout? = null

    /**
     * The layout for the current layers of the nested icon. A deferred icon changes them when it is resolved, and a theme
     * change can create them again, so the layout is computed again when they change.
     */
    override val layout: LayerLayout
        get() {
            val source = layoutSource(renderer)
            cachedLayout?.let { if (source === cachedLayoutSource) return it }
            val consumed = consumedSpace(renderer)
            val layout = applyLayout(layer.modifier, consumed.width, consumed.height)
            cachedLayoutSource = source
            cachedLayout = layout
            return layout
        }

    override fun render(paintingContext: LayerPaintingContext) {
        val placement = layout.placement(paintingContext)
        val nestedLayerContext =
            paintingContext.createNestedLayer(placement.bounds.x, placement.bounds.y, scale = placement.scale, alpha = layout.alpha)
        renderer.render(nestedLayerContext)
    }
}

/** The space that [renderer] takes, before the density and the scale are known. */
private fun consumedSpace(renderer: IconRenderer?): CompoundDimensions =
    when (renderer) {
        null -> CompoundDimensions(0.dp.compoundSize(), 0.dp.compoundSize())
        is DefaultIconRenderer -> renderer.consumedSpace()
        is DefaultDeferredIconRenderer -> consumedSpace(renderer.currentRenderer)
        else -> error("Unsupported renderer: $renderer")
    }

/** An object that changes when the layers that [renderer] renders change. */
private fun layoutSource(renderer: IconRenderer?): Any? =
    when (renderer) {
        is DefaultIconRenderer -> renderer.layerRenderersForLayout
        is DefaultDeferredIconRenderer -> layoutSource(renderer.currentRenderer)
        else -> renderer
    }
