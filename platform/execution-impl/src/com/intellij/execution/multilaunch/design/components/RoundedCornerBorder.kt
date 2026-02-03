package com.intellij.execution.multilaunch.design.components

import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.border.Border

@ApiStatus.Internal
class RoundedCornerBorder(private val cornerRadius: Int) : Border {

  override fun getBorderInsets(c: java.awt.Component): java.awt.Insets {
    return JBUI.insets(cornerRadius, cornerRadius, cornerRadius, cornerRadius)
  }

  override fun isBorderOpaque(): Boolean {
    return true
  }

  override fun paintBorder(c: java.awt.Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
    val g2d = g as Graphics2D
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g2d.color = c.background
    g2d.fillRoundRect(x, y, width - 1, height - 1, cornerRadius, cornerRadius)
  }
}