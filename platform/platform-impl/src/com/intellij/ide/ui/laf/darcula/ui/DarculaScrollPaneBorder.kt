// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf.darcula.ui

import com.intellij.ide.ui.laf.darcula.DarculaNewUIUtil
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.openapi.ui.ErrorBorderCapable
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import java.awt.geom.Area
import java.awt.geom.RoundRectangle2D
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.border.Border
import javax.swing.plaf.UIResource

@Suppress("unused")
@ApiStatus.Internal
class DarculaScrollPaneBorder : Border, UIResource, ErrorBorderCapable {

  override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
    if (c !is JScrollPane || g !is Graphics2D) {
      return
    }

    val textArea = getTextArea(c)
    if (textArea == null) {
      g.color = JBColor.border()
      g.drawRect(x, y, width - 1, height - 1)
    }
    else {
      paintTextAreaBorder(c, textArea, g, x, y, width, height)
    }
  }

  override fun getBorderInsets(c: Component): Insets {
    val inset = (if (c is JScrollPane) getVisualPadding(c) else 0) + 1
    return JBInsets(inset)
  }

  override fun isBorderOpaque(): Boolean {
    return false
  }

  fun getVisualPadding(c: JScrollPane): Int {
    return if (getTextArea(c) == null) 0 else 2
  }

  private fun paintTextAreaBorder(c: JScrollPane, textArea: JTextArea, g: Graphics2D, x: Int, y: Int, width: Int, height: Int) {
    val r = Rectangle(x, y, width, height)
    JBInsets.removeFrom(r, getBorderInsets(c))
    JBInsets.addTo(r, JBUI.insets(1))

    // Fill outside part
    val arc = DarculaUIUtil.COMPONENT_ARC.float
    val shape = Area(Rectangle(x, y, width, height))
    shape.subtract(Area(RoundRectangle2D.Float(r.x + 0.5f, r.y + 0.5f, r.width - 1f, r.height - 1f, arc, arc)))
    g.color = c.parent?.background ?: c.background
    g.fill(shape)

    // Paint border
    val outline = DarculaUIUtil.getOutline(textArea)
    DarculaNewUIUtil.paintComponentBorder(g, r, outline, textArea.hasFocus(), textArea.isEnabled)
  }

  private fun getTextArea(c: Component): JTextArea? {
    if (c !is JBScrollPane) {
      // Don't support focus ring for JScrollPane either, because JScrollPane doesn't support painting the border over children
      return null
    }

    return c.viewport.view as? JTextArea
  }
}
