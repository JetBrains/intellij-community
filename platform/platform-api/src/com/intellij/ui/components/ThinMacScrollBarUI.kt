// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components

import com.intellij.ui.components.ScrollBarPainter.ThinScrollBarThumb
import com.intellij.util.ui.JBUI
import java.awt.Graphics2D
import java.awt.Insets
import javax.swing.JComponent

private const val DEFAULT_THICKNESS: Int = 3

internal open class ThinMacScrollBarUI : MacScrollBarUI {
  internal constructor() : super(DEFAULT_THICKNESS, DEFAULT_THICKNESS, DEFAULT_THICKNESS)

  constructor(thickNess: Int, thicknessMax: Int, thicknessMin: Int) : super(thickNess, thicknessMax, thicknessMin)

  override fun createThumbPainter(state: DefaultScrollbarUiInstalledState): ScrollBarPainter.Thumb {
    return ThinScrollBarThumb({ state.scrollBar }, false, state.coroutineScope)
  }

  public override fun paintTrack(g: Graphics2D, c: JComponent) {
    // track is not needed
  }

  override fun getInsets(small: Boolean): Insets = JBUI.emptyInsets()

  override fun updateStyle(style: MacScrollbarStyle?) {
    super.updateStyle(MacScrollbarStyle.Overlay)
  }
}