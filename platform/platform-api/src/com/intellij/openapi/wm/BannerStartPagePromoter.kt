// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.openapi.ui.popup.IconButton
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.InplaceButton
import com.intellij.ui.JBColor
import com.intellij.ui.components.panels.BackgroundRoundedPanel
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.dsl.gridLayout.GridLayout
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.UnscaledGapsY
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import com.intellij.ui.dsl.gridLayout.toUnscaledGaps
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
import org.jetbrains.annotations.Nls
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.event.ActionEvent
import javax.swing.*

abstract class BannerStartPagePromoter : StartPagePromoter {

  override fun getPromotion(isEmptyState: Boolean): JComponent {
    // todo: almost all should be rewritten with Kotlin UI DSL, when it's moved to from IMPL to API module
    val headerPanel: JPanel = NonOpaquePanel()
    headerPanel.layout = BoxLayout(headerPanel, BoxLayout.X_AXIS)
    headerPanel.alignmentX = Component.LEFT_ALIGNMENT


    headerPanel.add(createHeader())
    headerPanel.add(Box.createHorizontalGlue())

    val hPanel: JPanel = BackgroundRoundedPanel(JBUI.scale(16)).also {
      UiNotifyConnector.installOn(it, object : Activatable {
        override fun showNotify() {
          onBannerShown()
        }

        override fun hideNotify() {
          onBannerHide()
        }
      })
    }

    closeAction?.let { closeAction ->
      val closeIcons = IconButton(IdeBundle.message("banner.button.close"), AllIcons.Actions.Close, AllIcons.Actions.CloseDarkGrey)
      val closeButton = InplaceButton(closeIcons) {
        closeAction(hPanel)
      }
      closeButton.maximumSize = Dimension(16, 16)
      headerPanel.add(closeButton)
    }

    val descriptionLabel = JLabel("<html>${description}</html>").also {
      it.alignmentX = Component.LEFT_ALIGNMENT
      it.font = JBUI.Fonts.label().deriveFont(JBUI.Fonts.label().size2D + (when {
        SystemInfo.isLinux -> JBUIScale.scale(-2)
        SystemInfo.isMac -> JBUIScale.scale(-1)
        else -> 0
      }))
      it.foreground = UIUtil.getContextHelpForeground()
      // todo workaround IJPL-62164 Implement minSize in GridLayout
      it.preferredSize = Dimension(100, 0)
    }
    val button = createButton()
    button.accessibleContext.accessibleDescription = "$headerLabel. $description"

    val vPanel = JPanel(GridLayout()).apply {
      isOpaque = false
      alignmentY = Component.TOP_ALIGNMENT
    }
    val builder = RowsGridBuilder(vPanel)
    builder
      .cell(headerPanel, horizontalAlign = HorizontalAlign.FILL, resizableColumn = true)
      .row(rowGaps = UnscaledGapsY(top = 4, bottom = 8))
      .cell(descriptionLabel, horizontalAlign = HorizontalAlign.FILL)
      .row()
      .cell(button, visualPaddings = button.insets.toUnscaledGaps())

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

  @RequiresEdt
  protected abstract fun runAction()

  protected open fun onBannerShown() {}

  protected open fun onBannerHide() {}

  protected open fun createHeader(): JLabel {
    val result = JLabel(headerLabel)
    val labelFont = StartupUiUtil.labelFont
    result.font = JBFont.create(labelFont).deriveFont(Font.BOLD).deriveFont(labelFont.size2D + JBUI.scale(2))
    return result
  }

  protected open fun createButton(): JComponent {
    val jButton = JButton()
    jButton.isOpaque = false
    jButton.alignmentX = Component.LEFT_ALIGNMENT
    jButton.action = object : AbstractAction(actionLabel) {
      override fun actionPerformed(e: ActionEvent?) {
        runAction()
      }
    }
    return jButton
  }
}