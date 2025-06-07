// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.bootstrap

import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.ide.ui.laf.darcula.ui.OnboardingDialogButtons.BUTTON_HOVER_BORDER_COLOR
import com.intellij.ide.ui.laf.darcula.ui.OnboardingDialogButtons.DEFAULT_BUTTON_HOVER_BORDER_COLOR
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBSwingUtilities
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.Nls
import java.awt.AWTEvent
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Graphics
import java.awt.event.AWTEventListener
import java.awt.event.ActionEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
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
  class MyButton : JButton() {
    var isPressed = false
    val eventListener: AWTEventListener = createAWTEventListener()

    private fun createAWTEventListener(): AWTEventListener {
      return object : AWTEventListener {
        override fun eventDispatched(event: AWTEvent?) {
          if (event is MouseEvent && event.id == MouseEvent.MOUSE_CLICKED) {
            val source = event.source
            if (source is Component) {
              val bounds = this@MyButton.bounds
              val location = SwingUtilities.convertPoint(source, event.point, this@MyButton.parent)
              if (!bounds.contains(location)) {
                isPressed = false
              }
            }
          }
        }
      }
    }

    override fun addNotify() {
      super.addNotify()
      toolkit.addAWTEventListener(eventListener, AWTEvent.MOUSE_EVENT_MASK)
    }

    override fun removeNotify() {
      super.removeNotify()
      toolkit.removeAWTEventListener(eventListener)
    }

    override fun getComponentGraphics(g: Graphics?): Graphics {
      return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(g))
    }

  }
  val btn = MyButton()
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
      btn.isPressed = !btn.isPressed
      if (btn.isPressed) {
        onClick.invoke(btn)
      }
    }
  }
  btn.addKeyListener(object : KeyAdapter() {
    override fun keyPressed(e: KeyEvent) {
      if (e.keyCode == KeyEvent.VK_ENTER) {
        onClick.invoke(btn)
      }
    }
  })
  btn.icon = icon
  btn.horizontalTextPosition = SwingConstants.LEFT
  return btn
}
