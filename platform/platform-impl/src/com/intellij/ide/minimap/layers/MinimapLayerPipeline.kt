// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.layers

import java.awt.Graphics2D

internal class MinimapLayerPipeline(
  layers: List<MinimapLayer>,
) {
  private val orderedLayers: List<MinimapLayer> = layers.sortedBy(MinimapLayer::order)

  fun paint(graphics: Graphics2D, state: MinimapLayerRenderState) {
    for (layer in orderedLayers) {
      if (!layer.isApplicable(state)) continue
      layer.paint(graphics, state)
    }
  }
}
