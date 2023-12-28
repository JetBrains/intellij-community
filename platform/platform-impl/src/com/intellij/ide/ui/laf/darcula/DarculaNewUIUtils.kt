// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf.darcula

import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.MacUIUtil
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import kotlin.math.max

/**
 * Paints non focused border rect inside [rect], outlined border can come outside
 */
internal fun paintBorder(g: Graphics, rect: Rectangle, outline: DarculaUIUtil.Outline?, focused: Boolean, enabled: Boolean) {
  val g2 = g.create() as Graphics2D

  try {
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                        if (MacUIUtil.USE_QUARTZ) RenderingHints.VALUE_STROKE_PURE else RenderingHints.VALUE_STROKE_NORMALIZE)

    val lw = DarculaUIUtil.LW.get()
    val bw = DarculaUIUtil.BW.get()
    val arc = DarculaUIUtil.COMPONENT_ARC.float

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
 * Using DarculaUIUtil.doPaint and similar methods doesn't give good results when line thickness is 1 (right corners too thin)
 */
private fun paintRectangle(g: Graphics2D, rect: Rectangle, arc: Float, thick: Int) {
  JBInsets.addTo(rect, JBUI.insets(thick - DarculaUIUtil.LW.get()))

  val w = thick.toFloat()
  val border = Path2D.Float(Path2D.WIND_EVEN_ODD)
  border.append(RoundRectangle2D.Float(0f, 0f, rect.width.toFloat(), rect.height.toFloat(), arc, arc), false)
  val innerArc = max(arc - thick * 2, 0.0f)
  border.append(RoundRectangle2D.Float(w, w, rect.width - w * 2, rect.height - w * 2, innerArc, innerArc), false)

  g.translate(rect.x, rect.y)
  g.fill(border)
}
