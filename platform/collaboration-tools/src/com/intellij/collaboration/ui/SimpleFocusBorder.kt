// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Insets
import javax.swing.border.Border

internal class SimpleFocusBorder : Border {
  override fun paintBorder(c: Component?, g: Graphics?, x: Int, y: Int, width: Int, height: Int) {
    if (c?.hasFocus() == true && g is Graphics2D) {
      DarculaUIUtil.paintFocusBorder(g, width, height, 0f, true)
    }
  }

  override fun getBorderInsets(c: Component): Insets {
    val bw = DarculaUIUtil.BW.float
    val lw = DarculaUIUtil.LW.float
    val insets = (bw + lw).toInt()
    return Insets(insets, insets, insets, insets)
  }

  override fun isBorderOpaque() = false
}