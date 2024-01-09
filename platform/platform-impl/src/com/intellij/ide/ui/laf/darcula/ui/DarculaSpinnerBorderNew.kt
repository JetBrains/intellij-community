// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf.darcula.ui

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.ide.ui.laf.darcula.paintComponentBorder
import com.intellij.openapi.ui.ErrorBorderCapable
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.Graphics
import java.awt.Insets
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.border.Border
import javax.swing.plaf.UIResource

/**
 * Spinner border for new UI themes
 */
@ApiStatus.Internal
internal class DarculaSpinnerBorderNew : Border, UIResource, ErrorBorderCapable {

  override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
    val r = Rectangle(x, y, width, height)
    JBInsets.removeFrom(r, getBorderInsets(c))
    val isFocused = DarculaSpinnerBorder.isFocused(c)
    paintComponentBorder(g, r, DarculaUIUtil.getOutline(c as JComponent), isFocused, c.isEnabled())
  }

  override fun getBorderInsets(c: Component): Insets {
    return JBUI.insets(3).asUIResource()
  }

  override fun isBorderOpaque(): Boolean {
    return true
  }
}
