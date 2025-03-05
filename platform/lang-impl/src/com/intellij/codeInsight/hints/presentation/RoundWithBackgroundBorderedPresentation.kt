// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.presentation

import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.util.ui.GraphicsUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Graphics2D

@ApiStatus.Internal
class RoundWithBackgroundBorderedPresentation(
  val presentation: RoundWithBackgroundPresentation,
  val borderColor: Color? = null,
) : InlayPresentation by presentation {
  override fun paint(g: Graphics2D, attributes: TextAttributes) {
    presentation.paint(g, attributes)
    if (borderColor != null) {
      val config = GraphicsUtil.setupAAPainting(g)
      g.color = borderColor
      g.drawRoundRect(0, 0, width, height, presentation.arcWidth, presentation.arcHeight)
      config.restore()
    }
  }
}