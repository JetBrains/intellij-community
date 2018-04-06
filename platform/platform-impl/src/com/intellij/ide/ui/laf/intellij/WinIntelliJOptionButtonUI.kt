/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.ide.ui.laf.intellij

import com.intellij.ide.ui.laf.intellij.WinIntelliJButtonUI.DISABLED_ALPHA_LEVEL
import com.intellij.ui.components.BasicOptionButtonUI
import com.intellij.util.ui.JBUI.scale
import java.awt.*
import javax.swing.AbstractButton
import javax.swing.JComponent
import javax.swing.UIManager

class WinIntelliJOptionButtonUI : BasicOptionButtonUI() {
  private val outerInsets get() = (mainButton.border as WinIntelliJButtonBorder).outerInsets
  private val lw get() = scale(1)

  override fun createMainButton() = object : MainButton() {
    override fun paintNotSimple(g: Graphics2D) {
      g.clipRect(0, 0, width - outerInsets.right, height)
//      background is painted in button ui

      super.paintNotSimple(g)
    }

    override fun paintBorderNotSimple(g: Graphics2D) = super.paintBorderNotSimple(g).also {
      // we do not need any rendering hints set by border here - so we clone again
      cloneAndPaint(g) { paintSeparatorArea(it, this) }
    }
  }

  override fun configureMainButton() = super.configureMainButton().also { mainButton.isOpaque = false }
  override fun unconfigureMainButton() = super.unconfigureMainButton().also { mainButton.isOpaque = true }

  override fun createArrowButton() = object : ArrowButton() {
    override fun paintNotSimple(g: Graphics2D) {
      val bw = scale(2)
      g.clipRect(outerInsets.left + bw, 0, width - (outerInsets.left + bw), height)
//      background is painted in button ui

      super.paintNotSimple(g)
      paintArrow(g, this)
    }
  }

  fun paintArrow(g: Graphics2D, b: AbstractButton) {
    val icon = WinIntelliJComboBoxUI.getArrowIcon(b)
    if (icon != null) {
      val x = scale(7)
      val y = (b.height - icon.iconHeight) / 2
      icon.paintIcon(b, g, x, y)
    }
  }

  override fun configureArrowButton() = super.configureArrowButton().also { arrowButton.isOpaque = false }
  override fun unconfigureArrowButton() = super.unconfigureArrowButton().also { arrowButton.isOpaque = true }

  override val arrowButtonPreferredSize get() = Dimension(scale(23), optionButton.preferredSize.height)

  override val showPopupXOffset get() = scale(4)

  override fun createLayoutManager() = object : OptionButtonLayout() {
    override fun layoutContainer(parent: Container) {
      val mainButtonWidth = optionButton.width - if (arrowButton.isVisible) arrowButton.preferredSize.width else 0
      val offset = if (arrowButton.isVisible) scale(2) else 0

      mainButton.bounds = Rectangle(offset, 0, mainButtonWidth, optionButton.height)
      arrowButton.bounds = Rectangle(mainButtonWidth - offset, 0, arrowButton.preferredSize.width, optionButton.height)
    }
  }

  fun paintSeparatorArea(g: Graphics2D, c: JComponent) {
    val bw = scale(WinIntelliJButtonBorder.getBorderWidth(mainButton))
    val x = mainButton.width - outerInsets.right - lw
    val y = outerInsets.top + bw
    val height = mainButton.height - (outerInsets.top + outerInsets.bottom + 2 * bw)

    paintSeparator(g, c, x, y, height)
    if (WinIntelliJButtonBorder.isWideBorder(mainButton)) {
      paintMainButtonBackground(g, x - 2 * lw, y, height)
    }
  }

  private fun paintSeparator(g: Graphics2D, c: JComponent, x: Int, y: Int, height: Int) {
    g.color = UIManager.getColor("Button.intellij.native.borderColor")
    if (!c.isEnabled) {
      g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, DISABLED_ALPHA_LEVEL)
    }
    g.fill(Rectangle(x, y, lw, height))
  }

  private fun paintMainButtonBackground(g: Graphics2D, x: Int, y: Int, height: Int) {
    g.color = mainButton.background
    g.fill(Rectangle(x, y, 2 * lw, height))
  }

  companion object {
    @Suppress("UNUSED_PARAMETER")
    @JvmStatic
    fun createUI(c: JComponent) = WinIntelliJOptionButtonUI()
  }
}