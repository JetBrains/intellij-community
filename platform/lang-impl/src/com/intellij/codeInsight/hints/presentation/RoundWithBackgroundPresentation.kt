// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints.presentation

import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.util.ui.GraphicsUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Graphics2D

class RoundWithBackgroundPresentation(
  presentation: InlayPresentation,
  override val arcWidth: Int,
  override val arcHeight: Int,
  color: Color? = null,
  backgroundAlpha : Float = 0.55f
) : AbstractRoundWithBackgroundPresentation(presentation, color, backgroundAlpha)

abstract class AbstractRoundWithBackgroundPresentation(
  presentation: InlayPresentation,
  val color: Color?,
  val backgroundAlpha : Float
) : StaticDelegatePresentation(presentation) {
  abstract val arcWidth: Int
  abstract val arcHeight: Int
  override fun paint(g: Graphics2D, attributes: TextAttributes) {
    val backgroundColor = color ?: attributes.backgroundColor
    if (backgroundColor != null) {
      val alpha = backgroundAlpha
      val config = GraphicsUtil.setupAAPainting(g)
      GraphicsUtil.paintWithAlpha(g, alpha)
      g.color = backgroundColor
      g.fillRoundRect(0, 0, width, height, arcWidth, arcHeight)
      config.restore()
    }
    presentation.paint(g, attributes)
  }
}