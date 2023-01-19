// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm

import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.popup.IconButton
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.InplaceButton
import com.intellij.ui.JBColor
import com.intellij.ui.components.panels.BackgroundRoundedPanel
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.Rectangle
import java.awt.event.ActionEvent
import javax.swing.*

abstract class BannerStartPagePromoter : StartPagePromoter {
  override fun getPromotion(isEmptyState: Boolean): JPanel? {
    if (!canCreatePromo(isEmptyState)) return null

    val vPanel: JPanel = NonOpaquePanel()
    vPanel.layout = BoxLayout(vPanel, BoxLayout.PAGE_AXIS)
    vPanel.alignmentY = Component.TOP_ALIGNMENT

    val headerPanel: JPanel = NonOpaquePanel()
    headerPanel.layout = BoxLayout(headerPanel, BoxLayout.X_AXIS)
    headerPanel.alignmentX = Component.LEFT_ALIGNMENT

    val header = JLabel(headerLabel)
    header.font = StartupUiUtil.getLabelFont().deriveFont(Font.BOLD).deriveFont(StartupUiUtil.getLabelFont().size2D + JBUI.scale(2))

    headerPanel.add(header)
    headerPanel.add(Box.createHorizontalGlue())

    val hPanel: JPanel = BackgroundRoundedPanel(JBUI.scale(16))

    closeAction?.let { closeAction ->
      val closeIcons = IconButton(null, AllIcons.Actions.Close, AllIcons.Actions.CloseDarkGrey)
      val closeButton = InplaceButton(closeIcons) {
        closeAction(hPanel)
      }
      closeButton.maximumSize = Dimension(16, 16)
      headerPanel.add(closeButton)
    }

    vPanel.add(headerPanel)
    vPanel.add(rigid(0, 4))
    val description = JLabel("<html>${description}</html>").also {
      it.alignmentX = Component.LEFT_ALIGNMENT
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
    jButton.alignmentX = Component.LEFT_ALIGNMENT
    jButton.action = object : AbstractAction(actionLabel) {
      override fun actionPerformed(e: ActionEvent?) {
        runAction()
      }
    }

    vPanel.add(Box.createVerticalGlue())
    vPanel.add(buttonPixelHunting(jButton))

    hPanel.background = JBColor.namedColor("WelcomeScreen.SidePanel.background", JBColor(0xF2F2F2, 0x3C3F41))
    hPanel.layout = BoxLayout(hPanel, BoxLayout.X_AXIS)
    hPanel.border = JBUI.Borders.empty(12, 16, 16, 16)
    val picture = JLabel(promoImage)
    picture.alignmentY = Component.TOP_ALIGNMENT
    hPanel.add(picture)
    hPanel.add(rigid(20, 0))
    hPanel.add(vPanel)

    return hPanel
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

  private fun rigid(width: Int, height: Int): Component {
    return scaledRigid(JBUI.scale(width), JBUI.scale(height))
  }

  private fun scaledRigid(width: Int, height: Int): Component {
    return (Box.createRigidArea(Dimension(width, height)) as JComponent).apply {
      alignmentX = Component.LEFT_ALIGNMENT
      alignmentY = Component.TOP_ALIGNMENT
    }
  }

  protected abstract val headerLabel: @Nls String
  protected abstract val actionLabel: @Nls String
  protected abstract val description: @Nls String
  protected abstract val promoImage: Icon

  protected open val closeAction: ((promoPanel: JPanel) -> Unit)? = null

  protected abstract fun runAction()
  protected open fun canCreatePromo(isEmptyState: Boolean): Boolean = isEmptyState
}