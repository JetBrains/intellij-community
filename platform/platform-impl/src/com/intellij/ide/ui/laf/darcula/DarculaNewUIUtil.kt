// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf.darcula

import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.MacUIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import kotlin.math.max

@ApiStatus.Internal
object DarculaNewUIUtil {
  /**
   * Paints rounded border for a focusable component. Non focused border rect is inside [rect], focused/outlined border can come outside
   */
  fun paintComponentBorder(g: Graphics, rect: Rectangle, outline: DarculaUIUtil.Outline?, focused: Boolean, enabled: Boolean,
                           bw: Int = DarculaUIUtil.BW.get()) {
    val g2 = g.create() as Graphics2D

    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                          if (MacUIUtil.USE_QUARTZ) RenderingHints.VALUE_STROKE_PURE else RenderingHints.VALUE_STROKE_NORMALIZE)

      val lw = DarculaUIUtil.LW.get()
      val arc = DarculaUIUtil.COMPONENT_ARC.get()

      when {
        enabled && outline != null -> {
          outline.setGraphicsColor(g2, focused)
          paintRectangle(g2, rect, arc, bw)
        }

        focused -> {
          DarculaUIUtil.Outline.focus.setGraphicsColor(g2, true)
          paintRectangle(g2, rect, arc, bw)
        }

        else -> {
          g2.color = DarculaUIUtil.getOutlineColor(enabled, focused)
          paintRectangle(g2, rect, arc, lw)
        }
      }
    }
    finally {
      g2.dispose()
    }
  }

  /**
   * Fills part of component inside border
   */
  fun fillInsideComponentBorder(g: Graphics, rect: Rectangle, color: Color) {
    if (rect.width <= 0 || rect.height <= 0) {
      return
    }

    val g2 = g.create() as Graphics2D

    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                          if (MacUIUtil.USE_QUARTZ) RenderingHints.VALUE_STROKE_PURE else RenderingHints.VALUE_STROKE_NORMALIZE)

      val arc = DarculaUIUtil.COMPONENT_ARC.float
      val border = Path2D.Float(Path2D.WIND_EVEN_ODD)
      // Reduce size a little bit, so inside background is not protruded outside the border near rounded corners
      border.append(RoundRectangle2D.Float(0.5f, 0.5f, rect.width - 1f, rect.height - 1f, arc, arc), false)
      g2.translate(rect.x, rect.y)
      g2.color = color
      g2.fill(border)
    }
    finally {
      g2.dispose()
    }
  }

  /**
   * Using DarculaUIUtil.doPaint and similar methods doesn't give good results when line thickness is 1 (right corners too thin)
   */
  private fun paintRectangle(g: Graphics2D, rect: Rectangle, arc: Int, thick: Int) {
    val addToRect = thick - DarculaUIUtil.LW.get()
    if (addToRect > 0) {
      @Suppress("UseDPIAwareInsets")
      JBInsets.addTo(rect, Insets(addToRect, addToRect, addToRect, addToRect))
    }

    val w = thick.toFloat()
    val border = Path2D.Float(Path2D.WIND_EVEN_ODD)
    border.append(RoundRectangle2D.Float(0f, 0f, rect.width.toFloat(), rect.height.toFloat(), arc.toFloat(), arc.toFloat()), false)
    val innerArc = max(arc.toFloat() - thick * 2, 0.0f)
    border.append(RoundRectangle2D.Float(w, w, rect.width - w * 2, rect.height - w * 2, innerArc, innerArc), false)

    g.translate(rect.x, rect.y)
    g.fill(border)
  }
}