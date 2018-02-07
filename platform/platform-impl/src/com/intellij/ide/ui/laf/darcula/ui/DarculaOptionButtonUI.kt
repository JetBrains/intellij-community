// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula.ui

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil.bw
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil.lw
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI.getDisabledTextColor
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI.getTextColor
import com.intellij.ui.components.BasicOptionButtonUI
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.scale
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Rectangle2D
import javax.swing.AbstractButton
import javax.swing.JComponent
import javax.swing.border.Border

open class DarculaOptionButtonUI : BasicOptionButtonUI() {
  protected open val clipXOffset = scale(7)

  private var optionButtonBorder: Border? = null

  override fun configureOptionButton() = super.configureOptionButton().also { optionButtonBorder = optionButton.border }
  override fun unconfigureOptionButton() = super.unconfigureOptionButton().also {
    optionButton.border = optionButtonBorder
    optionButtonBorder = null
  }

  override fun createMainButton() = object : MainButton() {
    override fun paintNotSimple(g: Graphics2D) {
      g.clipRect(0, 0, width - clipXOffset, height)
      paintBackground(g, this)

      super.paintNotSimple(g)
    }
  }

  override fun configureMainButton() = super.configureMainButton().also { mainButton.isOpaque = false }
  override fun unconfigureMainButton() = super.unconfigureMainButton().also { mainButton.isOpaque = true }

  override fun createArrowButton() = object : ArrowButton() {
    override fun paintNotSimple(g: Graphics2D) {
      g.clipRect(clipXOffset, 0, width - clipXOffset, height)
      paintBackground(g, this)

      super.paintNotSimple(g)
      paintArrow(g, this)
    }
  }

  protected open fun paintArrow(g: Graphics2D, b: AbstractButton) {
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE)

    g.color = if (b.isEnabled) getTextColor(b) else getDisabledTextColor()
    g.fill(DarculaComboBoxUI.getArrowShape(b))
  }

  override fun configureArrowButton() = super.configureArrowButton().also { arrowButton.isOpaque = false }
  override fun unconfigureArrowButton() = super.unconfigureArrowButton().also { arrowButton.isOpaque = true }

  override val arrowButtonPreferredSize get() = Dimension(scale(27), optionButton.preferredSize.height)

  override val showPopupXOffset get() = JBUI.scale(3)

  override fun paint(g: Graphics, c: JComponent) {
    if (!isSimpleButton) paintSeparatorArea(g as Graphics2D, c)
  }

  protected open fun paintSeparatorArea(g: Graphics2D, c: JComponent) {
    g.clipRect(mainButton.width - clipXOffset, 0, 2 * clipXOffset, c.height)
    paintBackground(g, c)

    mainButton.ui.paint(g, c)
    paintSeparator(g, c)
  }

  protected open fun paintSeparator(g: Graphics2D, c: JComponent) {
    val yOffset = bw() + lw(g) + scale(1)
    val x = mainButton.width.toFloat()

    g.color = (mainButton.border as DarculaButtonPainter).getBorderColor(c)
    g.fill(Rectangle2D.Float(x, yOffset, lw(g), mainButton.height - yOffset * 2))
  }

  override fun updateOptions() = super.updateOptions().also {
    optionButton.border = if (isSimpleButton) optionButtonBorder else mainButton.border
  }

  companion object {
    @Suppress("UNUSED_PARAMETER")
    @JvmStatic
    fun createUI(c: JComponent) = DarculaOptionButtonUI()
  }
}