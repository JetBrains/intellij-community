// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.openapi.wm.impl.status.IdeStatusBarImpl
import com.intellij.ui.AppUIUtil
import com.intellij.ui.ComponentUtil
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.JBColor
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.TexturePaint
import java.awt.geom.Rectangle2D
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.border.Border
import javax.swing.border.CompoundBorder
import kotlin.math.floor

/**
 * @author Alexander Lobas
 */
@ApiStatus.Internal
open class RiderWidgetLabel(text: @Nls String, private val clientSide: Boolean) : JLabel(text) {
  companion object {
    fun createStatusBarBorder(): Border {
      if (ExperimentalUI.Companion.isNewUI()) {
        return CompoundBorder(JBUI.Borders.customLine(JBUI.CurrentTheme.StatusBar.BORDER_COLOR, 1, 0, 0, 0), JBUI.Borders.emptyLeft(10))
      }
      return JBUI.Borders.emptyTop(1)
    }
  }

  override fun addNotify() {
    super.addNotify()
    if (clientSide) {
      (parent as JComponent).border = null
      (parent.parent as JComponent).border = null

      val statusBar = ComponentUtil.findParentByCondition(parent) { c -> c is IdeStatusBarImpl }
      if (statusBar is IdeStatusBarImpl) {
        statusBar.border = createStatusBarBorder()
      }
    }
  }

  private val startColor1 = JBColor(0xFFB801, 0xFFB801)
  private val endColor1 = JBColor(0xFF0B68, 0xFF0B68)
  private val startColor2 = JBColor(Color(0xF7, 0xF8, 0xFA, 0xEE), Color(0x2B, 0x2D, 0x30, 0xEE))
  private val endColor2 = JBColor(Color(0xF7, 0xF8, 0xFA, 0xCC), Color(0x2B, 0x2D, 0x30, 0xCC))
  private val hoverColor = JBColor(Color(0, 0, 0, 0x12), Color(0xFF, 0xFF, 0xFF, 0x16))

  private var isBright = JBColor.isBright()
  private var texture1: TexturePaint? = null
  private var texture2: TexturePaint? = null
  private var listener = true
  private var hovered = false

  override fun paintComponent(g: Graphics) {
    if (listener) {
      listener = false
      val container = if (clientSide) parent.parent else parent
      container.addPropertyChangeListener("TextPanel.widgetEffect") { event ->
        hovered = event.newValue != null
      }
    }

    val g2d = g.create() as Graphics2D
    try {
      val fillWidth = width + 1
      val realWidth = floor(JBUIScale.sysScale(g2d) * fillWidth).toInt()
      if (realWidth != texture1?.image?.width || isBright != JBColor.isBright()) {
        texture1 = AppUIUtil.createHorizontalGradientTexture(g2d, startColor1, endColor1, fillWidth, -1, 0)
        texture2 = AppUIUtil.createHorizontalGradientTexture(g2d, startColor2, endColor2, fillWidth, -1, 0)
        isBright = JBColor.isBright()
      }

      val rect = Rectangle2D.Float(0f, 0f, width.toFloat(), height.toFloat())
      g2d.paint = texture1
      g2d.fill(rect)
      g2d.paint = texture2
      g2d.fill(rect)

      if (hovered) {
        g2d.color = hoverColor
        g2d.fill(rect)
      }
    }
    finally {
      g2d.dispose()
    }
    super.paintComponent(g)
  }
}