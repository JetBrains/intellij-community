// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.newUiOnboarding

import com.intellij.ide.ui.experimental.meetNewUi.MeetNewUIAction
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.*
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.dsl.gridLayout.UnscaledGapsY
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Font
import javax.swing.JComponent
import javax.swing.JRootPane
import javax.swing.border.Border

class NewUiOnboardingDialog(project: Project)
  : DialogWrapper(project, null, false, IdeModalityType.PROJECT, false) {
  init {
    init()
    setUndecorated(true)
    rootPane.windowDecorationStyle = JRootPane.NONE
    rootPane.border = PopupBorder.Factory.create(true, true)
    WindowRoundedCornersManager.configure(this)
  }

  override fun createCenterPanel(): JComponent {
    // todo: replace with separate banner when it will be ready
    val banner = IconLoader.getIcon("expui/meetNewUi/banner.png", MeetNewUIAction::class.java.classLoader)
    val contentGaps = UnscaledGaps(28, 32, 22, 32)

    val panel = panel {
      row {
        icon(banner)
          .customize(UnscaledGaps.EMPTY)
          .applyToComponent { WindowMoveListener(this).installTo(this) }
      }
      panel {
        row {
          label(NewUiOnboardingBundle.message("dialog.title"))
            .customize(UnscaledGaps.EMPTY)
            .applyToComponent {
              font = JBFont.label().deriveFont(Font.BOLD, JBUIScale.scale(20f))
            }
        }
        row {
          val maxWidth = banner.iconWidth - JBUI.scale(contentGaps.width)
          val charWidth = window.getFontMetrics(JBFont.label()).charWidth('0')
          val maxLineLength = maxWidth / charWidth
          text(NewUiOnboardingBundle.message("dialog.text"), maxLineLength)
            .customize(UnscaledGaps(top = 8))
        }
        row {
          button(NewUiOnboardingBundle.message("start.tour")) { close(0) }
            .focused()
            .applyToComponent {
              // make button blue without an outline
              putClientProperty("gotItButton", true)
              ClientProperty.put(this, DarculaButtonUI.DEFAULT_STYLE_KEY, true)
              // register Enter key binding
              this@NewUiOnboardingDialog.rootPane.defaultButton = this
            }

          link(NewUiOnboardingBundle.message("dialog.skip")) { close(1) }

          customize(UnscaledGapsY(top = 12))
        }
        customize(contentGaps)
      }
    }

    panel.background = JBColor.namedColor("NewUiOnboarding.Dialog.background", UIUtil.getPanelBackground())
    return panel
  }

  override fun createContentPaneBorder(): Border? = null
}