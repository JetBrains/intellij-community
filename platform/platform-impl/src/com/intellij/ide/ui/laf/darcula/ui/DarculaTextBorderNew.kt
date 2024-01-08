// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf.darcula.ui

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.ide.ui.laf.darcula.paintComponentBorder
import com.intellij.util.ui.JBInsets
import java.awt.Graphics2D
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.text.JTextComponent

/**
 * Text border for new UI
 */
class DarculaTextBorderNew : DarculaTextBorder() {

  override fun paintNormalBorder(g: Graphics2D, c: JComponent, r: Rectangle) {
    JBInsets.removeFrom(r, getBorderInsets(c))
    val isFocused = isFocused(c)
    val editable = c !is JTextComponent || c.isEditable
    paintComponentBorder(g, r, DarculaUIUtil.getOutline(c), isFocused, c.isEnabled && editable)
  }
}
