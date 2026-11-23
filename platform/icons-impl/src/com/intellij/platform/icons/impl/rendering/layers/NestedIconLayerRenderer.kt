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

    /** The layouts of the nested layers that [cachedLayout] was computed from, see [layoutSource]. */
    private var cachedLayoutSource: List<Any?>? = null
    private var cachedLayout: LayerLayout? = null

    /**
     * The layout for the current layouts of the nested icon. A deferred icon changes them when it is resolved, at any
     * depth, and a theme change can create them again. So the layout is computed again when one of them changes.
     */
    override val layout: LayerLayout
        get() {
            val source = layoutSource(renderer)
            cachedLayout?.let { if (sameLayouts(source, cachedLayoutSource)) return it }
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

/**
 * The current layouts of the layers that [renderer] renders, or the renderer itself for another renderer. The layout of a
 * nested layer reads the layouts of its own layers, so a change at any depth changes this list.
 */
private fun layoutSource(renderer: IconRenderer?): List<Any?> =
    when (renderer) {
        is DefaultIconRenderer -> renderer.layerLayouts
        is DefaultDeferredIconRenderer -> listOf(renderer.currentRenderer) + layoutSource(renderer.currentRenderer)
        else -> listOf(renderer)
    }

/** Whether [layouts] holds the same objects as [cached], in the same order. */
internal fun sameLayouts(layouts: List<Any?>, cached: List<Any?>?): Boolean =
    cached != null && layouts.size == cached.size && layouts.indices.all { layouts[it] === cached[it] }
