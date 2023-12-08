// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components

import com.intellij.ui.MixedColorProducer
import com.intellij.ui.components.ScrollBarPainter.ThinScrollBarThumb
import com.intellij.util.ui.JBUI
import java.awt.Graphics2D
import java.awt.Insets
import javax.swing.JComponent

internal class TabMacScrollBarUI(thickness: Int, thicknessMax: Int, thicknessMin: Int) : ThinMacScrollBarUI(thickness, thicknessMax,
                                                                                                            thicknessMin) {
  private var isHovered: Boolean = false
  private val defaultColorProducer: MixedColorProducer = MixedColorProducer(
    ScrollBarPainter.getColor({ myScrollBar }, ScrollBarPainter.TABS_TRANSPARENT_THUMB_BACKGROUND),
    ScrollBarPainter.getColor({ myScrollBar }, ScrollBarPainter.TABS_THUMB_BACKGROUND))
  private val hoveredColorProducer: MixedColorProducer = MixedColorProducer(
    ScrollBarPainter.getColor({ myScrollBar }, ScrollBarPainter.TABS_THUMB_BACKGROUND),
    ScrollBarPainter.getColor({ myScrollBar }, ScrollBarPainter.THUMB_HOVERED_BACKGROUND))

  override fun createThumbPainter(): ScrollBarPainter.Thumb {
    return object : ThinScrollBarThumb({ myScrollBar }, false) {
      override fun getFillProducer() = if (isHovered) hoveredColorProducer else defaultColorProducer
    }
  }

  override fun createWrapAnimationBehaviour(): ScrollBarAnimationBehavior {
    return object : ToggleableScrollBarAnimationBehaviorDecorator(createBaseAnimationBehavior(), myTrack.animator, myThumb.animator) {
      override fun onThumbHover(hovered: Boolean) {
        super.onThumbHover(hovered)
        if (isHovered != hovered) {
          isHovered = hovered
          myScrollBar.revalidate()
          myScrollBar.repaint()
        }
      }
    }
  }


  override fun paintThumb(g: Graphics2D?, c: JComponent?) {
    if (myAnimationBehavior.thumbFrame > 0) {
      paint(myThumb, g, c, !isHovered)
    }
  }

  override fun getInsets(small: Boolean): Insets {
    return if (small) JBUI.insetsBottom(2) else JBUI.emptyInsets()
  }
}