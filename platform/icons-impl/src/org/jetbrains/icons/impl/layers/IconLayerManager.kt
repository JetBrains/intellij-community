// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.layers

import org.jetbrains.icons.rendering.RenderingContext
import org.jetbrains.icons.impl.rendering.layers.IconLayerRenderer
import org.jetbrains.icons.layers.IconLayer

interface IconLayerManager {
  fun createRenderer(layer: IconLayer, renderingContext: RenderingContext): IconLayerRenderer

  companion object {
    @Volatile
    private var instance: IconLayerManager? = null

    @JvmStatic
    fun getInstance(): IconLayerManager = instance ?: error("IconLayerRendererManager is not initialized")

    fun setInstance(manager: IconLayerManager) {
      instance = manager
    }

    fun createRenderers(
        layers: List<IconLayer>,
        renderingContext: RenderingContext,
    ): List<IconLayerRenderer> {
      val instance = getInstance()
      return layers.map { instance.createRenderer(it, renderingContext) }
    }
  }
}