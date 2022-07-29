// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm

import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.CurrentTheme.Button.buttonOutlineColorStart
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.Rectangle
import java.awt.event.ActionEvent
import javax.swing.*
import javax.swing.border.MatteBorder

abstract class BannerStartPagePromoter : StartPagePromoter {
  override fun getPromotionForInitialState(): JPanel? {
    val rPanel: JPanel = NonOpaquePanel()
    rPanel.layout = BoxLayout(rPanel, BoxLayout.PAGE_AXIS)
    rPanel.border = JBUI.Borders.empty(JBUI.scale(10), JBUI.scale(32))

    val vPanel: JPanel = NonOpaquePanel()
    vPanel.layout = BoxLayout(vPanel, BoxLayout.PAGE_AXIS)
    vPanel.alignmentY = Component.TOP_ALIGNMENT

    val header = JLabel(getHeaderLabel())
    header.font = StartupUiUtil.getLabelFont().deriveFont(Font.BOLD).deriveFont(StartupUiUtil.getLabelFont().size2D + JBUI.scale(4))
    vPanel.add(header)
    vPanel.add(rigid(0, 4))
    val description = JLabel(
      "<html>${getDescription()}</html>").also {
      it.font = JBUI.Fonts.label().deriveFont(JBUI.Fonts.label().size2D + (when {
        SystemInfo.isLinux -> JBUIScale.scale(-2)
        SystemInfo.isMac -> JBUIScale.scale(-1)
        else -> 0
      }))
      it.foreground = UIUtil.getContextHelpForeground()
    }
    vPanel.add(description)
    val jButton = JButton()
    jButton.isOpaque = false
    jButton.action = object : AbstractAction(getActionLabel()) {
      override fun actionPerformed(e: ActionEvent?) {
        runAction()
      }
    }
    vPanel.add(rigid(0, 18))
    vPanel.add(buttonPixelHunting(jButton))

    val hPanel: JPanel = NonOpaquePanel()
    hPanel.layout = BoxLayout(hPanel, BoxLayout.X_AXIS)
    hPanel.add(vPanel)
    hPanel.add(Box.createHorizontalGlue())
    hPanel.add(rigid(20, 0))
    val picture = JLabel(promoImage())
    picture.alignmentY = Component.TOP_ALIGNMENT
    hPanel.add(picture)

    rPanel.add(NonOpaquePanel().apply {
      border = MatteBorder(JBUI.scale(1), 0, 0, 0, outLineColor())
    })
    rPanel.add(rigid(0, 20))
    rPanel.add(hPanel)
    return rPanel
  }

  private fun buttonPixelHunting(button: JButton): JPanel {

    val buttonSizeWithoutInsets = Dimension(button.preferredSize.width - button.insets.left - button.insets.right,
                                            button.preferredSize.height - button.insets.top - button.insets.bottom)

    val buttonPlace = JPanel().apply {
      layout = null
      maximumSize = buttonSizeWithoutInsets
      preferredSize = buttonSizeWithoutInsets
      minimumSize = buttonSizeWithoutInsets
      isOpaque = false
      alignmentX = JPanel.LEFT_ALIGNMENT
    }

    buttonPlace.add(button)
    button.bounds = Rectangle(-button.insets.left, -button.insets.top, button.preferredSize.width, button.preferredSize.height)

    return buttonPlace
  }

  fun rigid(width: Int, height: Int): Component {
    return scaledRigid(JBUI.scale(width), JBUI.scale(height))
  }

  fun scaledRigid(width: Int, height: Int): Component {
    return (Box.createRigidArea(Dimension(width, height)) as JComponent).apply {
      alignmentX = Component.LEFT_ALIGNMENT
      alignmentY = Component.TOP_ALIGNMENT
    }
  }

  abstract fun getHeaderLabel(): String
  abstract fun getActionLabel(): String
  abstract fun runAction()
  abstract fun getDescription(): String
  abstract fun promoImage(): Icon
  protected open fun outLineColor() = buttonOutlineColorStart(false)
}