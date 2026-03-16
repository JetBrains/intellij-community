// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui

import com.intellij.ui.RoundedLineBorder
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Component
import java.awt.Graphics

internal class FocusAwareRoundedLineBorder(
  color: Color,
  arcDiameter: Int = 1,
  thickness: Int = 1,
) : RoundedLineBorder(color, arcDiameter, thickness) {
  override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
    super.paintBorder(c, g, x, y, width, height)
  }

  override fun getColorToDraw(c: Component): Color {
    return if (c.hasFocus()) {
      JBUI.CurrentTheme.Focus.focusColor()
    }
    else {
      super.getColorToDraw(c)
    }
  }
}
