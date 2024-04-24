// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf.darcula.ui

import com.intellij.openapi.wm.impl.IdeBackgroundUtil
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBSwingUtilities
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.Nls
import java.awt.Color
import java.awt.Graphics
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

object OnboardingDialogButtons {
  fun createLinkButton(@Nls text: String, icon: Icon?, onClick: Runnable?): JButton {
    val btn = createLinkButton()
    onClick?.let {runnable ->
      btn.action = object : AbstractAction(text, icon) {
        override fun actionPerformed(e: ActionEvent) {
          runnable.run()
        }
      }
    }

    return btn
  }

  fun createLinkButton(): JButton {
    val btn = JButton()

    btn.putClientProperty("ActionToolbar.smallVariant", true)
    btn.putClientProperty(DarculaButtonUI.AVOID_EXTENDING_BORDER_GRAPHICS, true)
    btn.setHorizontalTextPosition(SwingConstants.LEFT)
    btn.setContentAreaFilled(false)
    btn.setForeground(JBUI.CurrentTheme.Link.Foreground.ENABLED)
    btn.isBorderPainted = false
    btn.iconTextGap = 0

    return btn
  }

  fun createHoveredLinkButton(): JButton {
    val btn = createLinkButton()
    btn.preferredSize = JBDimension(280, 40)
    btn.isBorderPainted = true
    btn.putClientProperty("JButton.backgroundColor", JBUI.CurrentTheme.ActionButton.hoverBackground())
    btn.putClientProperty("JButton.borderColor", Color(0, true))

    val listener: MouseAdapter = object : MouseAdapter() {
      override fun mouseEntered(e: MouseEvent) {
        btn.setContentAreaFilled(true)
        btn.repaint()
      }

      override fun mouseExited(e: MouseEvent) {
        btn.setContentAreaFilled(false)
        btn.repaint()
      }
    }
    btn.addMouseMotionListener(listener)
    btn.addMouseListener(listener)

    return btn
  }

  @JvmStatic
  fun createHoveredLinkButton(@Nls text: String, icon: Icon?, onClick: Runnable?): JButton {
    val btn = createHoveredLinkButton()

    onClick?.let {runnable ->
      btn.action = object : AbstractAction(text, icon) {
        override fun actionPerformed(e: ActionEvent) {
          runnable.run()
        }
      }
    }

    return btn
  }

  val BUTTON_HOVER_BORDER_COLOR: Color = JBColor(0xa8adbd, 0x6f737a)
  val DEFAULT_BUTTON_HOVER_BORDER_COLOR: Color = JBColor(0xa8adbd, 0x6f737a)

  @JvmStatic
  fun createMainButton(@Nls text: String, icon: Icon?, onClick: Runnable? = null): JButton {
    return createButton(true, text, icon, onClick)
  }

  @JvmStatic
  fun createMainButton(action: Action): JButton {
    return createButton(true, action)
  }

  @JvmStatic
  fun createButton(@Nls text: String, icon: Icon?, onClick: Runnable? = null): JButton {
    return createButton(false, text, icon, onClick)
  }

  @JvmStatic
  fun createButton(action: Action): JButton {
    return createButton(false, action)
  }

  private fun createButton(isDefault: Boolean, action: Action): JButton {
    val btn = createButton(isDefault)
    btn.action = action
    return btn
  }

  private fun createButton(isDefault: Boolean, @Nls text: String, icon: Icon?, onClick: Runnable? = null): JButton {
    return createButton(isDefault, object : AbstractAction(text, icon) {
      override fun actionPerformed(e: ActionEvent) {
        onClick?.run()
      }
    })
  }

  fun createButton(isDefault: Boolean): JButton {
    val btn = object: JButton() {
      override fun getComponentGraphics(g: Graphics?): Graphics {
        return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(g))
      }
    }
    btn.putClientProperty("ActionToolbar.smallVariant", true)
    btn.putClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY, isDefault)
    btn.putClientProperty(DarculaButtonUI.AVOID_EXTENDING_BORDER_GRAPHICS, true)
    btn.putClientProperty(IdeBackgroundUtil.NO_BACKGROUND, isDefault)
    val listener: MouseAdapter = object : MouseAdapter() {
      override fun mouseEntered(e: MouseEvent) {
        btn.putClientProperty("JButton.borderColor",
                              if (isDefault) DEFAULT_BUTTON_HOVER_BORDER_COLOR else BUTTON_HOVER_BORDER_COLOR)
        btn.repaint()
      }

      override fun mouseExited(e: MouseEvent) {
        btn.putClientProperty("JButton.borderColor", null)
        btn.repaint()
      }
    }
    btn.addMouseMotionListener(listener)
    btn.addMouseListener(listener)
    return btn
  }

}