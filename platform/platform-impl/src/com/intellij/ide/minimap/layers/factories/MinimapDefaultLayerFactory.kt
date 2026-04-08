// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.layers.factories

import com.intellij.ide.minimap.MinimapLayerPainter
import com.intellij.ide.minimap.MinimapPanel
import com.intellij.ide.minimap.layers.MinimapLayer
import com.intellij.ide.minimap.layers.MinimapLayerFactory
import com.intellij.ide.minimap.layers.MinimapLayerId
import com.intellij.ide.minimap.layers.MinimapLayerRenderState
import java.awt.Graphics2D

internal abstract class MinimapDefaultLayerFactory(
  final override val id: MinimapLayerId,
  final override val order: Int,
  private val requiredLegacyMode: Boolean? = null,
) : MinimapLayerFactory {
  final override fun createLayer(panel: MinimapPanel): MinimapLayer {
    val layerId = id
    val layerOrder = order
    val layerPainter = panel.layerPainter
    return object : MinimapLayer {
      override val id: MinimapLayerId = layerId
      override val order: Int = layerOrder

      override fun isApplicable(state: MinimapLayerRenderState): Boolean {
        return isLayerModeApplicable(state) && isAdditionalApplicable(state)
      }

      override fun paint(graphics: Graphics2D, state: MinimapLayerRenderState) {
        this@MinimapDefaultLayerFactory.paint(layerPainter, graphics, state)
      }
    }
  }

  open fun isAdditionalApplicable(state: MinimapLayerRenderState): Boolean = true

  abstract fun paint(layerPainter: MinimapLayerPainter, graphics: Graphics2D, state: MinimapLayerRenderState)

  private fun isLayerModeApplicable(state: MinimapLayerRenderState): Boolean {
    return requiredLegacyMode?.let { it == state.isLegacyMode } ?: true
  }
}
