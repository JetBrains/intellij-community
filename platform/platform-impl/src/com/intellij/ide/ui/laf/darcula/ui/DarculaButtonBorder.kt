// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf.darcula.ui

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.ide.ui.laf.darcula.paintComponentBorder
import com.intellij.util.ui.JBInsets
import org.jetbrains.annotations.ApiStatus
import java.awt.Graphics2D
import java.awt.Rectangle
import javax.swing.JComponent

/**
 * Button border for new UI themes
 */
// todo remove inheritance from DarculaButtonPainter, remove paintNormalFocusBorder, make public API
@ApiStatus.Internal
internal class DarculaButtonBorder: DarculaButtonPainter() {

  override fun paintNormalFocusBorder(g: Graphics2D, c: JComponent, r: Rectangle): Boolean {
    if (DarculaButtonUI.isDefaultButton(c)) {
      return false
    }

    JBInsets.removeFrom(r, getBorderInsets(c))
    paintComponentBorder(g, r, DarculaUIUtil.Outline.focus, true, true)
    return true
  }
}
