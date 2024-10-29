// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.bootstrap

import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.ide.ui.laf.darcula.ui.OnboardingDialogButtons.BUTTON_HOVER_BORDER_COLOR
import com.intellij.ide.ui.laf.darcula.ui.OnboardingDialogButtons.DEFAULT_BUTTON_HOVER_BORDER_COLOR
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBSwingUtilities
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Graphics
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

internal class ButtonPanel(@JvmField val button: JButton) : JPanel(BorderLayout(0, 0)) {
  init {
    val buttonGap = 4
    preferredSize = JBDimension(280, 40 + (buttonGap * 2))
    isOpaque = false
    add(button)
    border = JBUI.Borders.empty(buttonGap, 0)
  }
}

internal fun createButton(isDefault: Boolean, @Nls text: String, icon: Icon? = null, onClick: (JButton) -> Unit): JButton {
  val btn = object : JButton() {
    override fun getComponentGraphics(g: Graphics?): Graphics {
      return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(g))
    }
  }
  btn.putClientProperty("ActionToolbar.smallVariant", true)
  btn.putClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY, isDefault)
  btn.putClientProperty(DarculaButtonUI.AVOID_EXTENDING_BORDER_GRAPHICS, true)
  val listener: MouseAdapter = object : MouseAdapter() {
    override fun mouseEntered(e: MouseEvent) {
      btn.putClientProperty("JButton.borderColor", if (isDefault) DEFAULT_BUTTON_HOVER_BORDER_COLOR else BUTTON_HOVER_BORDER_COLOR)
      btn.repaint()
    }

    override fun mouseExited(e: MouseEvent) {
      btn.putClientProperty("JButton.borderColor", null)
      btn.repaint()
    }
  }
  btn.addMouseMotionListener(listener)
  btn.addMouseListener(listener)
  btn.action = object : AbstractAction(text, null) {
    override fun actionPerformed(e: ActionEvent) {
      onClick.invoke(btn)
    }
  }
  btn.icon = icon
  btn.horizontalTextPosition = SwingConstants.LEFT
  return btn
}
