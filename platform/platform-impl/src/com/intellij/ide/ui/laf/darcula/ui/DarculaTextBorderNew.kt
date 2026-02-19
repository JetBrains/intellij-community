// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf.darcula.ui

import com.intellij.ide.ui.laf.darcula.DarculaNewUIUtil
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.util.ui.JBInsets
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.text.JTextComponent

/**
 * Text border for new UI themes
 */
// todo remove inheritance from DarculaTextBorder, rename, make public API
@ApiStatus.Internal
internal class DarculaTextBorderNew : DarculaTextBorder() {

  override fun paintNormalBorder(g: Graphics2D, c: JComponent, r: Rectangle) {
    JBInsets.removeFrom(r, getBorderInsets(c))
    val isFocused = isFocused(c)
    val editable = c !is JTextComponent || c.isEditable
    DarculaNewUIUtil.paintComponentBorder(g, r, DarculaUIUtil.getOutline(c), isFocused, c.isEnabled && editable)
  }

  override fun paintSearchArea(g: Graphics2D, r: Rectangle, c: JTextComponent, fillBackground: Boolean) {
    JBInsets.removeFrom(r, getBorderInsets(c))
    if (fillBackground) {
      DarculaNewUIUtil.fillInsideComponentBorder(g, r, c.background)
    }
    DarculaNewUIUtil.paintComponentBorder(g, r, DarculaUIUtil.getOutline(c), c.hasFocus(), c.isEnabled && c.isEditable)
  }

  /**
   * Paints background of text field. Should take into account the shape of the border (like rounded borders and insets)
   */
  protected fun paintTextBackground(g: Graphics2D, component: JTextComponent, color: Color) {
    val r = Rectangle(component.size)
    JBInsets.removeFrom(r, getBorderInsets(component))
    DarculaNewUIUtil.fillInsideComponentBorder(g, r, color)
  }
}