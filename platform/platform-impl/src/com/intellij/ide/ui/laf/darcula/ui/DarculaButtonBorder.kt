// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf.darcula.ui

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.ide.ui.laf.darcula.paintComponentBorder
import com.intellij.util.ui.JBInsets
import java.awt.Graphics2D
import java.awt.Rectangle
import javax.swing.JComponent

/**
 * Button border for new UI themes
 */
class DarculaButtonBorder: DarculaButtonPainter() {

  override fun paintNormalFocusBorder(g: Graphics2D, c: JComponent, r: Rectangle): Boolean {
    if (DarculaButtonUI.isDefaultButton(c)) {
      return false
    }

    JBInsets.removeFrom(r, getBorderInsets(c))
    val isDefaultButton = DarculaButtonUI.isDefaultButton(c)
    val outline = if (isDefaultButton) DarculaUIUtil.Outline.defaultButton else DarculaUIUtil.Outline.focus
    paintComponentBorder(g, r, outline, true, true)
    return true
  }
}
