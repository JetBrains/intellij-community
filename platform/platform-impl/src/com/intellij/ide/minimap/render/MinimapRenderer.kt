// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.render

import com.intellij.ide.minimap.layout.MinimapLayoutMetrics
import com.intellij.util.ui.GraphicsUtil
import java.awt.Graphics2D
import java.awt.RenderingHints

class MinimapRenderer {
  fun paint(graphics: Graphics2D,
            context: MinimapRenderContext,
            entries: List<MinimapRenderEntry>,
            metrics: MinimapLayoutMetrics?) {
    val config = GraphicsUtil.setupAAPainting(graphics).apply {
      setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
      setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    }

    val colorContext = MinimapTokenColorContext(context, metrics)

    try {
      GraphicsUtil.paintWithAlpha(graphics, TOKEN_FILLER_ALPHA) {
        for (entry in entries) {
          graphics.color = colorContext.colorFor(entry)
          graphics.fill(entry.rect2d)
        }
      }
    }
    finally {
      config.restore()
    }
  }

  companion object {
    private const val TOKEN_FILLER_ALPHA: Float = 0.85f
  }
}
