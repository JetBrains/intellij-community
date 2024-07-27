// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components

import com.intellij.ui.MixedColorProducer
import com.intellij.util.ui.JBUI
import java.awt.Graphics2D
import java.awt.Insets
import javax.swing.JComponent

internal class TabScrollBarUI(
  thickness: Int,
  thicknessMax: Int,
  thicknessMin: Int,
) : ThinScrollBarUI(thickness = thickness, thicknessMax = thicknessMax, thicknessMin = thicknessMin) {
  private var isHovered: Boolean = false

  private val defaultColorProducer: MixedColorProducer = MixedColorProducer(
    ScrollBarPainter.getColor({ scrollBar }, ScrollBarPainter.TABS_TRANSPARENT_THUMB_BACKGROUND),
    ScrollBarPainter.getColor({ scrollBar }, ScrollBarPainter.TABS_THUMB_BACKGROUND))

  private val hoveredColorProducer: MixedColorProducer = MixedColorProducer(
    ScrollBarPainter.getColor({ scrollBar }, ScrollBarPainter.TABS_THUMB_BACKGROUND),
    ScrollBarPainter.getColor({ scrollBar }, ScrollBarPainter.TABS_THUMB_HOVERED_BACKGROUND))

  override fun createThumbPainter(): ScrollBarPainter.Thumb {
    return object : ScrollBarPainter.ThinScrollBarThumb({ scrollBar }, false) {
      override fun getFillProducer() = if (isHovered) hoveredColorProducer else defaultColorProducer
    }
  }

  override fun createWrapAnimationBehaviour(): ScrollBarAnimationBehavior {
    return object : ToggleableScrollBarAnimationBehaviorDecorator(createBaseAnimationBehavior(), myTrack.animator, thumb.animator) {
      override fun onThumbHover(hovered: Boolean) {
        super.onThumbHover(hovered)
        if (isHovered != hovered) {
          isHovered = hovered
          scrollBar!!.revalidate()
          scrollBar!!.repaint()
        }
      }
    }
  }


  override fun paintThumb(g: Graphics2D, c: JComponent) {
    if (animationBehavior != null && animationBehavior!!.thumbFrame > 0) {
      paint(thumb, g, c, !isHovered)
    }
  }

  override fun getInsets(small: Boolean): Insets {
    return if (small) JBUI.insetsBottom(2) else JBUI.emptyInsets()
  }
}