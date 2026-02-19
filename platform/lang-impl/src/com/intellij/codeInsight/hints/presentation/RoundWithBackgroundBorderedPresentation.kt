// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.presentation

import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.util.ui.GraphicsUtil
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D

class RoundWithBackgroundBorderedPresentation(
  presentation: AbstractRoundWithBackgroundPresentation,
  val borderColor: Color? = null,
  val borderWidth: Int = 1,
) : StaticDelegatePresentation(presentation) {
  override fun paint(g: Graphics2D, attributes: TextAttributes) {
    val roundWithBackgroundPresentation = presentation as AbstractRoundWithBackgroundPresentation
    roundWithBackgroundPresentation.paint(g, attributes)
    val borderColor = borderColor ?: attributes.effectColor
    if (borderColor != null) {
      val config = GraphicsUtil.setupAAPainting(g)
      g.color = borderColor
      g.stroke = BasicStroke(borderWidth.toFloat())
      g.drawRoundRect(0, 0, width, height,
                      roundWithBackgroundPresentation.arcWidth, roundWithBackgroundPresentation.arcHeight)
      config.restore()
    }
  }
}