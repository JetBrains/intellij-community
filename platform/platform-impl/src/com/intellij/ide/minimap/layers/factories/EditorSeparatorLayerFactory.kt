// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.layers.factories

import com.intellij.ide.minimap.MinimapPanel
import com.intellij.ide.minimap.layers.MinimapLayer
import com.intellij.ide.minimap.layers.MinimapLayerFactory
import com.intellij.ide.minimap.layers.MinimapLayerId
import com.intellij.ide.minimap.layers.MinimapLayerIds
import com.intellij.ide.minimap.layers.MinimapLayerRenderState
import com.intellij.ide.minimap.settings.MinimapSettings
import com.intellij.openapi.ui.OnePixelDivider
import com.intellij.util.ui.JBUI
import java.awt.Graphics2D

internal class EditorSeparatorLayerFactory : MinimapLayerFactory {
  override val id: MinimapLayerId = MinimapLayerIds.EDITOR_SEPARATOR
  override val order: Int = 100
  private val leftBorder = JBUI.Borders.customLineLeft(OnePixelDivider.BACKGROUND)

  override fun createLayer(panel: MinimapPanel): MinimapLayer {
    return object : MinimapLayer {
      override val id: MinimapLayerId = this@EditorSeparatorLayerFactory.id
      override val order: Int = this@EditorSeparatorLayerFactory.order

      override fun isApplicable(state: MinimapLayerRenderState): Boolean {
        val minimapState = MinimapSettings.getInstance().state
        return minimapState.rightAligned && minimapState.insideScrollbar
      }

      override fun paint(graphics: Graphics2D, state: MinimapLayerRenderState) {
        leftBorder.paintBorder(panel, graphics, 0, 0, panel.width, panel.height)
      }
    }
  }
}
