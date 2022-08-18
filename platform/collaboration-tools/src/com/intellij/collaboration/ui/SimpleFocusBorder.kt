// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Insets
import javax.swing.border.Border

class SimpleFocusBorder : Border {
  override fun paintBorder(c: Component?, g: Graphics?, x: Int, y: Int, width: Int, height: Int) {
    if (c?.hasFocus() == true && g is Graphics2D) {
      DarculaUIUtil.paintFocusBorder(g, width, height, 0f, true)
    }
  }

  override fun getBorderInsets(c: Component): Insets {
    val g2d = c.graphics as? Graphics2D ?: return JBInsets.emptyInsets()

    val bw = if (UIUtil.isUnderDefaultMacTheme()) JBUIScale.scale(3).toFloat() else DarculaUIUtil.BW.float
    val f = if (UIUtil.isRetina(g2d)) 0.5f else 1.0f
    val lw = if (UIUtil.isUnderDefaultMacTheme()) JBUIScale.scale(f) else DarculaUIUtil.LW.float
    val insets = (bw + lw).toInt()
    return Insets(insets, insets, insets, insets)
  }

  override fun isBorderOpaque() = false
}