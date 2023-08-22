// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.icons

import com.intellij.ui.BadgeShapeProvider
import com.intellij.ui.scale.ScaleType
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBFont
import java.awt.Color
import java.awt.Graphics2D
import javax.swing.Icon

class TextHoledIcon(icon: Icon,
                    val text: String,
                    val fontSize: Float,
                    private val plainColor: Color,
                    private val provider: BadgeShapeProvider) : HoledIcon(icon) {
  override fun copyWith(icon: Icon): Icon {
    TODO("Not yet implemented")
  }

  override fun createHole(width: Int, height: Int) = provider.createShape(width = width, height = height, hole = true)

  override fun paintHole(g: Graphics2D, width: Int, height: Int) {
    val shape = provider.createShape(width, height, false) ?: return
    val bounds = shape.bounds
    val (x, y) = bounds.x to bounds.y

    val customG = g.create() as Graphics2D
    try {
      val font = JBFont.create(JBFont.label().deriveFont(scaleVal(fontSize.toDouble(), ScaleType.OBJ_SCALE).toFloat()).asBold())
      val lineMetrics = font.getLineMetrics(text, customG.fontRenderContext)
      lineMetrics.ascent
      GraphicsUtil.setupAntialiasing(customG)
      customG.font = font
      val xText = scaleVal(x.toDouble(), ScaleType.OBJ_SCALE).toInt()
      val textHeight = lineMetrics.ascent.toInt()
      val yText = scaleVal(y.toDouble(), ScaleType.OBJ_SCALE).toInt() + textHeight
      customG.color = plainColor
      customG.drawString(text, xText, yText)
    }
    finally {
      customG.dispose()
    }
  }

  override fun replaceBy(replacer: IconReplacer): Icon {
    return TextHoledIcon(icon = replacer.replaceIcon(icon), text = text, fontSize = fontSize, plainColor = plainColor, provider = provider)
  }
}