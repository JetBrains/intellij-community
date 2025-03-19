// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components

import com.intellij.ui.components.ScrollBarPainter.ThinScrollBarThumb
import com.intellij.util.ui.JBUI
import java.awt.Graphics2D
import java.awt.Insets
import javax.swing.JComponent

private const val DEFAULT_THICKNESS = 3

internal open class ThinScrollBarUI : DefaultScrollBarUI {
  internal constructor() : super(thickness = DEFAULT_THICKNESS, thicknessMax = DEFAULT_THICKNESS, thicknessMin = DEFAULT_THICKNESS)

  constructor(thickness: Int, thicknessMax: Int, thicknessMin: Int) : super(
    thickness = thickness,
    thicknessMax = thicknessMax,
    thicknessMin = thicknessMin,
  )

  override fun createThumbPainter(state: DefaultScrollbarUiInstalledState): ScrollBarPainter.Thumb {
    return ThinScrollBarThumb({ state.scrollBar }, false, state.coroutineScope)
  }

  override fun paintTrack(g: Graphics2D, c: JComponent) {
    // track is not needed
  }

  override fun paintThumb(g: Graphics2D, c: JComponent, state: DefaultScrollbarUiInstalledState) {
    if (isOpaque(c)) {
      paint(p = state.thumb, g = g, c = c, small = ScrollSettings.isThumbSmallIfOpaque.invoke())
    }
    else if (state.animationBehavior.thumbFrame > 0) {
      paint(p = state.thumb, g = g, c = c, small = false)
    }
  }

  override fun getInsets(small: Boolean): Insets = JBUI.emptyInsets()
}