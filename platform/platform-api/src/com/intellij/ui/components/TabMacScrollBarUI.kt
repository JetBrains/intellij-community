// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components

import com.intellij.ui.MixedColorProducer
import com.intellij.ui.components.ScrollBarPainter.ThinScrollBarThumb
import com.intellij.util.ui.JBUI
import java.awt.Graphics2D
import java.awt.Insets
import javax.swing.JComponent

internal class TabMacScrollBarUI(
  thickness: Int,
  thicknessMax: Int,
  thicknessMin: Int,
) : ThinMacScrollBarUI(thickness, thicknessMax, thicknessMin) {
  private var isHovered: Boolean = false

  override fun createThumbPainter(state: DefaultScrollbarUiInstalledState): ScrollBarPainter.Thumb {
    val defaultColorProducer = MixedColorProducer(
      ScrollBarPainter.getColor({ state.scrollBar }, ScrollBarPainter.TABS_TRANSPARENT_THUMB_BACKGROUND),
      ScrollBarPainter.getColor({ state.scrollBar }, ScrollBarPainter.TABS_THUMB_BACKGROUND),
    )
    val hoveredColorProducer = MixedColorProducer(
      ScrollBarPainter.getColor({ state.scrollBar }, ScrollBarPainter.TABS_THUMB_BACKGROUND),
      ScrollBarPainter.getColor({ state.scrollBar }, ScrollBarPainter.TABS_THUMB_HOVERED_BACKGROUND),
    )

    return object : ThinScrollBarThumb({ state.scrollBar }, false, state.coroutineScope) {
      override fun getFillProducer() = if (isHovered) hoveredColorProducer else defaultColorProducer
    }
  }

  override fun createWrapAnimationBehaviour(state: DefaultScrollbarUiInstalledState): ScrollBarAnimationBehavior {
    return object : ToggleableScrollBarAnimationBehaviorDecorator(
      decoratedBehavior = createBaseAnimationBehavior(state),
      trackAnimator = state.track.animator,
      thumbAnimator = state.thumb.animator,
    ) {
      override fun onThumbHover(hovered: Boolean) {
        super.onThumbHover(hovered)
        if (isHovered != hovered) {
          isHovered = hovered
          state.scrollBar.revalidate()
          state.scrollBar.repaint()
        }
      }
    }
  }


  override fun paintThumb(g: Graphics2D, c: JComponent, state: DefaultScrollbarUiInstalledState) {
    if (state.animationBehavior.thumbFrame > 0) {
      paint(p = state.thumb, g = g, c = c, small = !isHovered)
    }
  }

  override fun getInsets(small: Boolean): Insets = if (small) JBUI.insetsBottom(2) else JBUI.emptyInsets()
}