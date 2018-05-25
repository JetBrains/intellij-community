// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.intellij

import com.intellij.ide.ui.laf.intellij.WinIntelliJButtonUI.DISABLED_ALPHA_LEVEL
import com.intellij.ui.components.BasicOptionButtonUI
import com.intellij.util.ui.JBUI.scale
import java.awt.*
import javax.swing.AbstractButton
import javax.swing.JComponent
import javax.swing.UIManager
import javax.swing.border.Border
import javax.swing.event.ChangeListener

class WinIntelliJOptionButtonUI : BasicOptionButtonUI() {
  private val outerInsets get() = mainButton.insets
  private val lw get() = scale(1)

  private var mainButtonBorder: Border? = null
  private var arrowButtonBorder: Border? = null
  private var mainButtonChangeListener: ChangeListener? = null
  private var arrowButtonChangeListener: ChangeListener? = null

  override fun createMainButton(): MainButton = object : MainButton() {
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

  override fun configureMainButton(): Unit = super.configureMainButton().also {
    mainButton.isOpaque = false
    mainButtonBorder = mainButton.border

    mainButtonChangeListener = createInnerButtonChangeListener().apply(mainButton::addChangeListener)
  }

  override fun unconfigureMainButton(): Unit = super.unconfigureMainButton().also {
    mainButton.removeChangeListener(mainButtonChangeListener)
    mainButtonChangeListener = null

    mainButton.isOpaque = true
    mainButton.border = mainButtonBorder
    mainButtonBorder = null
  }

  override fun createArrowButton(): ArrowButton = object : ArrowButton() {
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

  override fun configureArrowButton(): Unit = super.configureArrowButton().also {
    arrowButton.isOpaque = false
    arrowButtonBorder = arrowButton.border

    arrowButtonChangeListener = createInnerButtonChangeListener().apply(arrowButton::addChangeListener)
  }

  override fun unconfigureArrowButton(): Unit = super.unconfigureArrowButton().also {
    arrowButton.removeChangeListener(arrowButtonChangeListener)
    arrowButtonChangeListener = null

    arrowButton.isOpaque = true
    arrowButton.border = arrowButtonBorder
    arrowButtonBorder = null
  }

  override val arrowButtonPreferredSize: Dimension get() = Dimension(scale(23), optionButton.preferredSize.height)

  override val showPopupXOffset: Int get() = scale(4)

  override fun createLayoutManager(): OptionButtonLayout = object : OptionButtonLayout() {
    override fun layoutContainer(parent: Container) {
      val mainButtonWidth = optionButton.width - if (arrowButton.isVisible) arrowButton.preferredSize.width else 0
      val offset = if (arrowButton.isVisible) scale(2) else 0

      mainButton.bounds = Rectangle(offset, 0, mainButtonWidth, optionButton.height)
      arrowButton.bounds = Rectangle(mainButtonWidth - offset, 0, arrowButton.preferredSize.width, optionButton.height)
    }
  }

  fun paintSeparatorArea(g: Graphics2D, c: JComponent) {
    val bw = scale((mainButton.border as ButtonBorder).getBorderWidth(mainButton))
    val x = mainButton.width - outerInsets.right - lw
    val y = outerInsets.top + bw
    val height = mainButton.height - (outerInsets.top + outerInsets.bottom + 2 * bw)

    paintSeparator(g, c, x, y, height)
    if ((mainButton.border as ButtonBorder).isWideBorder(mainButton)) {
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

  override fun updateOptions(): Unit = super.updateOptions().also {
    mainButton.border = if (isSimpleButton) mainButtonBorder else ButtonBorder()
    arrowButton.border = if (isSimpleButton) arrowButtonBorder else ButtonBorder()
  }

  private fun createInnerButtonChangeListener() = ChangeListener {
    mainButton.repaint()
    arrowButton.repaint()
  }

  private inner class ButtonBorder : WinIntelliJButtonBorder() {
    public override fun isWideBorder(b: AbstractButton) = super.isWideBorder(arrowButton) && super.isWideBorder(mainButton)
  }

  companion object {
    @Suppress("UNUSED_PARAMETER")
    @JvmStatic
    fun createUI(c: JComponent): WinIntelliJOptionButtonUI = WinIntelliJOptionButtonUI()
  }
}