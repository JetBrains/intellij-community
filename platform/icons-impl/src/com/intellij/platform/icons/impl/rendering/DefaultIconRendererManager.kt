// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.rendering

import com.intellij.platform.icons.Icon
import com.intellij.platform.icons.impl.DefaultDeferredIcon
import com.intellij.platform.icons.impl.DefaultLayeredIcon
import com.intellij.platform.icons.impl.layers.AnimatedIconLayer
import com.intellij.platform.icons.impl.layers.ImageIconLayer
import com.intellij.platform.icons.impl.layers.LayoutIconLayer
import com.intellij.platform.icons.impl.layers.NestedIconLayer
import com.intellij.platform.icons.impl.layers.ShapeIconLayer
import com.intellij.platform.icons.impl.layers.SpacerIconLayer
import com.intellij.platform.icons.impl.rendering.layers.AnimatedIconLayerRenderer
import com.intellij.platform.icons.impl.rendering.layers.IconLayerManager
import com.intellij.platform.icons.impl.rendering.layers.IconLayerRenderer
import com.intellij.platform.icons.impl.rendering.layers.ImageIconLayerRenderer
import com.intellij.platform.icons.impl.rendering.layers.LayoutIconLayerRenderer
import com.intellij.platform.icons.impl.rendering.layers.NestedIconLayerRenderer
import com.intellij.platform.icons.impl.rendering.layers.ShapeIconLayerRenderer
import com.intellij.platform.icons.impl.rendering.layers.SpacerIconLayerRenderer
import com.intellij.platform.icons.layers.IconLayer
import com.intellij.platform.icons.rendering.IconRenderer
import com.intellij.platform.icons.rendering.IconRendererManager
import com.intellij.platform.icons.rendering.RenderingContext

abstract class DefaultIconRendererManager : IconRendererManager, IconLayerManager {
    init {
        IconLayerManager.setInstance(this)
    }

    override fun createRenderer(icon: Icon, context: RenderingContext): IconRenderer =
        createRendererOrNull(icon, context) ?: error("Unsupported icon type: $icon")

    protected fun createRendererOrNull(icon: Icon, context: RenderingContext): IconRenderer? =
        when (icon) {
            is DefaultLayeredIcon -> DefaultIconRenderer(icon, context)
            is DefaultDeferredIcon -> createDeferredIconRenderer(icon, context)
            else -> null
        }

    private fun createDeferredIconRenderer(icon: DefaultDeferredIcon, context: RenderingContext): IconRenderer {
        val renderer = DefaultDeferredIconRenderer(icon, context)
        icon.addDoneListener(renderer)
        return renderer
    }

    override fun createRenderer(layer: IconLayer, renderingContext: RenderingContext): IconLayerRenderer {
        if (renderingContext !is DefaultRenderingContext) error("Unsupported rendering context: $renderingContext")
        return when (layer) {
            is ImageIconLayer -> {
                ImageIconLayerRenderer(layer, renderingContext)
            }
            is NestedIconLayer -> {
                NestedIconLayerRenderer(layer, renderingContext)
            }
            is LayoutIconLayer -> {
                LayoutIconLayerRenderer(layer, renderingContext)
            }
            is AnimatedIconLayer -> {
                AnimatedIconLayerRenderer(layer, renderingContext)
            }
            is ShapeIconLayer -> {
                ShapeIconLayerRenderer(layer, renderingContext)
            }
            is SpacerIconLayer -> {
                SpacerIconLayerRenderer(layer)
            }
            else -> error("Unsupported icon layer type: $layer")
        }
    }
}
