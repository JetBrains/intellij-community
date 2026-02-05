// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.rendering.layers

import com.intellij.platform.icons.layers.IconLayer
import com.intellij.platform.icons.rendering.RenderingContext

interface IconLayerManager {
    fun createRenderer(layer: IconLayer, renderingContext: RenderingContext): IconLayerRenderer

    companion object {
        @Volatile private var instance: IconLayerManager? = null

        @JvmStatic
        fun getInstance(): IconLayerManager = instance ?: error("IconLayerRendererManager is not initialized")

        fun setInstance(manager: IconLayerManager) {
            instance = manager
        }

        fun createRenderers(layers: List<IconLayer>, renderingContext: RenderingContext): List<IconLayerRenderer> {
            val instance = getInstance()
            return layers.map { instance.createRenderer(it, renderingContext) }
        }
    }
}
