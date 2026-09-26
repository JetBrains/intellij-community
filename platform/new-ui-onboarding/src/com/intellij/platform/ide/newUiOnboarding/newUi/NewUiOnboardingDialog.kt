// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.newUiOnboarding.newUi

import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingBundle
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingUtil
import com.intellij.ui.ClientProperty
import com.intellij.ui.JBColor
import com.intellij.ui.PopupBorder
import com.intellij.ui.WindowRoundedCornersManager
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.dsl.gridLayout.UnscaledGapsY
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Font
import javax.swing.JComponent
import javax.swing.JRootPane
import javax.swing.border.Border


internal class NewUiOnboardingDialog(private val project: Project)
  : DialogWrapper(project, null, false, IdeModalityType.IDE, false) {
  private val backgroundColor: Color
    get() = JBColor.namedColor("NewUiOnboarding.Dialog.background", UIUtil.getPanelBackground())

  init {
    init()
    setUndecorated(true)
    rootPane.windowDecorationStyle = JRootPane.NONE
    rootPane.border = PopupBorder.Factory.create(true, true)
    WindowRoundedCornersManager.configure(this)
  }

  override fun createCenterPanel(): JComponent {
    val contentGaps = UnscaledGaps(28, 32, 22, 32)
    val popupImage = NewUiOnboardingUtil.getImage(NewUiOnboardingUtil.MEET_ISLANDS_TOUR_COVER_IMAGE_PATH)
    val panel = panel {
      row {
        icon(popupImage)
          .customize(UnscaledGaps.EMPTY)
      }
      panel {
        row {
          label(NewUiOnboardingBundle.message("newUiOnboarding.dialog.title"))
            .customize(UnscaledGaps.EMPTY)
            .applyToComponent {
              font = JBFont.label().deriveFont(Font.BOLD, JBUIScale.scale(20f))
            }
        }
        row {
          val maxWidth = popupImage.iconWidth - JBUI.scale(contentGaps.width)
          val charWidth = window.getFontMetrics(JBFont.label()).charWidth('0')
          val maxLineLength = maxWidth / charWidth
          text(NewUiOnboardingBundle.message("newUiOnboarding.dialog.text"), maxLineLength)
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

    panel.background = backgroundColor
    return panel
  }

  override fun createContentPaneBorder(): Border? = null
}