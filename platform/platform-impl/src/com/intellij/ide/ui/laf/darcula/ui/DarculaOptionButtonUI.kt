// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil.*
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI.getDisabledTextColor
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI.isDefaultButton
import com.intellij.openapi.util.IconLoader.getDisabledIcon
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.components.BasicOptionButtonUI
import com.intellij.ui.icons.toStrokeIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.scale
import java.awt.*
import java.awt.geom.Rectangle2D
import javax.swing.AbstractButton
import javax.swing.JComponent
import javax.swing.UIManager
import javax.swing.border.Border

open class DarculaOptionButtonUI : BasicOptionButtonUI() {
  protected open val clipXOffset: Int = scale(7)

  private var optionButtonBorder: Border? = null

  override fun configureOptionButton(): Unit = super.configureOptionButton().also {
    optionButtonBorder = optionButton.border
    optionButton.border = if (isSimpleButton) optionButtonBorder else mainButton.border
  }

  override fun unconfigureOptionButton(): Unit = super.unconfigureOptionButton().also {
    optionButton.border = optionButtonBorder
    optionButtonBorder = null
  }

  override fun createMainButton(): MainButton = object : MainButton() {
    override fun paintNotSimple(g: Graphics2D) {
      g.clipRect(0, 0, width - clipXOffset, height)
      paintBackground(g, this)

      super.paintNotSimple(g)
    }
  }

  override fun configureMainButton(): Unit = super.configureMainButton().also { mainButton.isOpaque = false }
  override fun unconfigureMainButton(): Unit = super.unconfigureMainButton().also { mainButton.isOpaque = true }

  override fun createArrowButton(): ArrowButton = object : ArrowButton() {
    override fun paintNotSimple(g: Graphics2D) {
      g.clipRect(clipXOffset, 0, width - clipXOffset, height)
      paintBackground(g, this)

      super.paintNotSimple(g)
      paintArrow(g, this)
    }
  }

  protected open fun paintArrow(g: Graphics2D, b: AbstractButton) {
    if (ExperimentalUI.isNewUI()) {
      val icon = if (b.isEnabled) {
        if (isDefaultButton(b)) toStrokeIcon(AllIcons.General.ChevronDown, JBUI.CurrentTheme.Button.Split.Default.ICON_COLOR)
        else AllIcons.General.ChevronDown
      }
      else getDisabledIcon(AllIcons.General.ChevronDown)
      val r = DarculaComboBoxUI.getArrowButtonRect(b)
      icon.paintIcon(b, g, r.x + (r.width - icon.iconWidth) / 2, r.y + (r.height - icon.iconHeight) / 2)
    }
    else {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE)

      g.color = if (b.isEnabled) getButtonTextColor(b) else getDisabledTextColor()
      g.fill(DarculaComboBoxUI.getArrowShape(b))
    }
  }

  override fun configureArrowButton(): Unit = super.configureArrowButton().also { arrowButton.isOpaque = false }
  override fun unconfigureArrowButton(): Unit = super.unconfigureArrowButton().also { arrowButton.isOpaque = true }

  override val arrowButtonPreferredSize: Dimension
    get() = Dimension(JBUI.CurrentTheme.Component.ARROW_AREA_WIDTH.get() + arrowButton.insets.right, optionButton.preferredSize.height)

  override val showPopupXOffset: Int get() = scale(if (ExperimentalUI.isNewUI()) 3 + JBUI.CurrentTheme.Popup.borderWidth().toInt() else 3)

  override fun paint(g: Graphics, c: JComponent) {
    if (!isSimpleButton) paintSeparatorArea(g as Graphics2D, c as AbstractButton)
  }

  protected open fun paintSeparatorArea(g: Graphics2D, c: JComponent) {
    g.clipRect(mainButton.width - clipXOffset, 0, 2 * clipXOffset, c.height)
    paintBackground(g, c)

    mainButton.ui.paint(g, c)
    paintSeparator(g, c)

    // clipXOffset is rather big and cuts arrow - so we also paint arrow part here
    g.translate(mainButton.width, 0)
    paintArrow(g, arrowButton)
  }

  protected open fun paintSeparator(g: Graphics2D, c: JComponent) {
    val yOffset = BW.float + LW.float + scale(if (ExperimentalUI.isNewUI()) 4 else 1)
    val x = mainButton.width.toFloat()

    g.paint = separatorColor(c)
    g.fill(Rectangle2D.Float(x, yOffset, LW.float, mainButton.height - yOffset * 2))
  }

  private fun separatorColor(c: JComponent) : Paint {
    c as AbstractButton
    val defButton = isDefaultButton(c)
    if (ExperimentalUI.isNewUI()) {
      if (defButton && c.isEnabled) return JBUI.CurrentTheme.Button.Split.Default.SEPARATOR_COLOR
      return UIManager.getColor("OptionButton.separatorColor") ?: (mainButton.border as DarculaButtonPainter).getBorderPaint(c, false)
    }


    val hasFocus = c.hasFocus()
    val resourceName = when {
      defButton && !hasFocus -> "OptionButton.default.separatorColor"
      !defButton && !hasFocus -> "OptionButton.separatorColor"
      else -> null
    }

    return resourceName?.let { UIManager.getColor(it) } ?: (mainButton.border as DarculaButtonPainter).getBorderPaint(c)
  }

  override fun updateOptions(): Unit = super.updateOptions().also {
    optionButton.border = if (isSimpleButton) optionButtonBorder else mainButton.border
  }

  companion object {
    @Suppress("UNUSED_PARAMETER")
    @JvmStatic
    fun createUI(c: JComponent): DarculaOptionButtonUI = DarculaOptionButtonUI()
  }
}