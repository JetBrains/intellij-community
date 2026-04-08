// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.layers.factories

import com.intellij.ide.minimap.MinimapLayerPainter
import com.intellij.ide.minimap.layers.MinimapLayerIds
import com.intellij.ide.minimap.layers.MinimapLayerRenderState
import java.awt.Graphics2D

internal class CaretLayerFactory : MinimapDefaultLayerFactory(
  id = MinimapLayerIds.CARET,
  order = 80,
) {
  override fun paint(layerPainter: MinimapLayerPainter, graphics: Graphics2D, state: MinimapLayerRenderState) {
    layerPainter.paintCaretLayer(graphics)
  }
}
