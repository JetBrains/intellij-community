// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.unified

import com.intellij.ide.ui.laf.darcula.DarculaNewUIUtil
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Graphics
import java.awt.Insets
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.border.Border

internal fun createUnifiedPluginFocusBorder(
  component: JComponent,
  contentBorder: Border,
  outlineInsets: Insets,
  arc: Int,
): Border {
  val contentInsets = contentBorder.getBorderInsets(component)
  return object : Border {
    override fun paintBorder(component: Component, graphics: Graphics, x: Int, y: Int, width: Int, height: Int) {
      contentBorder.paintBorder(component, graphics, x, y, width, height)
      if (!component.isEnabled) return

      val lineWidth = JBUI.scale(FOCUS_LINE_WIDTH)
      // The painter expands a thick line beyond its rectangle. Include this expansion to keep the visible gap at one pixel.
      val lineExpansion = (lineWidth - DarculaUIUtil.LW.get()).coerceAtLeast(0)
      val focusOffset = JBUI.scale(FOCUS_GAP) + lineExpansion
      val focusRect = Rectangle(
        x + outlineInsets.left + focusOffset,
        y + outlineInsets.top + focusOffset,
        width - outlineInsets.left - outlineInsets.right - focusOffset * 2,
        height - outlineInsets.top - outlineInsets.bottom - focusOffset * 2,
      )
      if (focusRect.width <= 0 || focusRect.height <= 0) return

      DarculaNewUIUtil.drawRoundedComponentRectangle(
        graphics,
        focusRect,
        JBUI.CurrentTheme.ActionButton.focusedBorder(),
        arc.toFloat(),
        lineWidth,
      )
    }

    @Suppress("UseDPIAwareInsets")
    override fun getBorderInsets(component: Component): Insets =
      Insets(contentInsets.top, contentInsets.left, contentInsets.bottom, contentInsets.right)

    override fun isBorderOpaque(): Boolean = false
  }
}

private const val FOCUS_GAP: Int = 1
private const val FOCUS_LINE_WIDTH: Int = 2
