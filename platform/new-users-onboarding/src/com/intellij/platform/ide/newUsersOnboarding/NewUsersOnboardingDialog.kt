// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.newUsersOnboarding

import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.IconLoader
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingBundle
import com.intellij.ui.ClientProperty
import com.intellij.ui.JBColor
import com.intellij.ui.PopupBorder
import com.intellij.ui.WindowMoveListener
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

internal class NewUsersOnboardingDialog(
  project: Project,
  private val onClose: (exitCode: Int) -> Unit,
) : DialogWrapper(project, null, false, IdeModalityType.MODELESS, false) {
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
    val popupImage = IconLoader.getIcon(IMAGE_PATH, NewUsersOnboardingDialog::class.java.classLoader)
    val panel = panel {
      row {
        icon(popupImage)
          .customize(UnscaledGaps.EMPTY)
          .applyToComponent {
            WindowMoveListener(this).installTo(this)
          }
      }
      panel {
        row {
          label(NewUsersOnboardingBundle.message("dialog.title", ApplicationNamesInfo.getInstance().fullProductName))
            .customize(UnscaledGaps.EMPTY)
            .applyToComponent {
              font = JBFont.label().deriveFont(Font.BOLD, JBUIScale.scale(20f))
            }
        }
        row {
          val maxWidth = popupImage.iconWidth - JBUI.scale(contentGaps.width)
          val charWidth = window.getFontMetrics(JBFont.label()).charWidth('0')
          val maxLineLength = maxWidth / charWidth
          text(NewUsersOnboardingBundle.message("dialog.text"), maxLineLength)
            .customize(UnscaledGaps(top = 8))
        }
        row {
          button(NewUiOnboardingBundle.message("start.tour")) { close(OK_EXIT_CODE) }
            .focused()
            .applyToComponent {
              // make button blue without an outline
              putClientProperty("gotItButton", true)
              ClientProperty.put(this, DarculaButtonUI.DEFAULT_STYLE_KEY, true)
              // register Enter key binding
              this@NewUsersOnboardingDialog.rootPane.defaultButton = this
            }

          link(NewUiOnboardingBundle.message("dialog.skip")) { close(CLOSE_EXIT_CODE) }

          customize(UnscaledGapsY(top = 12))
        }
        customize(contentGaps)
      }
    }

    panel.background = backgroundColor
    return panel
  }

  override fun dispose() {
    super.dispose()
    onClose(exitCode)
  }

  override fun createContentPaneBorder(): Border? = null

  companion object {
    private const val IMAGE_PATH: String = "newUiOnboarding/meetIslandsTourCover.png"

    const val CLOSE_EXTERNALLY: Int = NEXT_USER_EXIT_CODE
  }
}