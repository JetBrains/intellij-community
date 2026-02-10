// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.rendering

import org.jetbrains.icons.Icon
import org.jetbrains.icons.rendering.IconRenderer
import org.jetbrains.icons.rendering.IconRendererManager
import org.jetbrains.icons.rendering.LoadingStrategy
import org.jetbrains.icons.impl.layers.AnimatedIconLayer
import org.jetbrains.icons.impl.DefaultLayeredIcon
import org.jetbrains.icons.impl.layers.IconIconLayer
import org.jetbrains.icons.impl.layers.ImageIconLayer
import org.jetbrains.icons.impl.layers.LayoutIconLayer
import org.jetbrains.icons.impl.rendering.layers.AnimatedIconLayerRenderer
import org.jetbrains.icons.impl.rendering.layers.IconIconLayerRenderer
import org.jetbrains.icons.layers.IconLayer
import org.jetbrains.icons.impl.rendering.layers.IconLayerRenderer
import org.jetbrains.icons.impl.layers.IconLayerManager
import org.jetbrains.icons.rendering.RenderingContext
import org.jetbrains.icons.impl.DefaultDynamicIcon
import org.jetbrains.icons.impl.layers.ShapeIconLayer
import org.jetbrains.icons.impl.rendering.layers.ShapeIconLayerRenderer
import org.jetbrains.icons.impl.rendering.layers.ImageIconLayerRenderer
import org.jetbrains.icons.impl.rendering.layers.LayoutIconLayerRenderer

abstract class DefaultIconRendererManager: IconRendererManager, IconLayerManager {
  init {
    IconLayerManager.setInstance(this)
  }

  override fun createRenderer(icon: Icon, context: RenderingContext, loadingStrategy: LoadingStrategy): IconRenderer {
    return createRendererOrNull(icon, context, loadingStrategy) ?: error("Unsupported icon type: $icon")
  }

  protected fun createRendererOrNull(icon: Icon, context: RenderingContext, loadingStrategy: LoadingStrategy): IconRenderer? {
    return when (icon) {
      is DefaultLayeredIcon -> DefaultIconRenderer(icon, context, loadingStrategy)
      is DefaultDynamicIcon -> DefaultDynamicIconRenderer(icon, context, loadingStrategy)
      else -> null
    }
  }

  override fun createRenderer(layer: IconLayer, renderingContext: RenderingContext): IconLayerRenderer {
    return createRendererOrNull(layer, renderingContext) ?: error("Unsupported icon layer type: $layer")
  }

  protected fun createRendererOrNull(layer: IconLayer, renderingContext: RenderingContext): IconLayerRenderer? {
    return when (layer) {
      is ImageIconLayer -> {
        ImageIconLayerRenderer(layer, renderingContext)
      }
      is IconIconLayer -> {
        IconIconLayerRenderer(layer, renderingContext)
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
      else -> null
    }
  }
}