// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.PlatformUtils
import com.intellij.util.ui.JBFont
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import java.awt.event.ActionListener
import javax.swing.JComponent

/**
 * @author Alexander Lobas
 */
@Internal
class SubscriptionExpirationDialog(project: Project?, private val showPromise: Boolean) :
  LicenseExpirationDialog(project, getImagePath(), 433, 242) {

  private var selectionButton = 0

  enum class ResultState {
    CANCEL,
    ACTIVATE,
    PROMISE,
    CONTINUE
  }

  companion object {
    private fun getImagePath(): String {
      if (PlatformUtils.isPyCharm()) {
        return "/images/PyCharmBannerLogo.png"
      }
      return "/images/IdeaBannerLogo.png"
    }

    @JvmStatic
    fun show(project: Project?, showPromise: Boolean): ResultState {
      val dialog = SubscriptionExpirationDialog(project, showPromise)
      dialog.show()

      if (dialog.exitCode != OK_EXIT_CODE) {
        return ResultState.CANCEL
      }

      return when (dialog.selectionButton) {
        0 -> ResultState.ACTIVATE
        1 -> ResultState.PROMISE
        else -> ResultState.CONTINUE
      }
    }
  }

  init {
    initDialog(IdeBundle.message("subscription.dialog.title"))
  }

  override fun createPanel(): JComponent {
    val panel = panel {
      row {
        label(IdeBundle.message("subscription.dialog.title")).component.font = JBFont.h1()
      }
      row {
        browserLink(IdeBundle.message("subscription.dialog.link", getPlatformName()), "https://www.jetbrains.com/idea/features")
        bottomGap(BottomGap.MEDIUM)
      }

      buttonsGroup {
        val listener = ActionListener { e ->
          selectionButton = when (e.actionCommand) {
            IdeBundle.message("subscription.dialog.activate.button", getPlatformName()) -> 0
            IdeBundle.message("subscription.dialog.promise.button") -> 1
            else -> 2
          }
          updateOKActionText()
        }

        row {
          radioButton(IdeBundle.message("subscription.dialog.activate.button", getPlatformName()), 0).component.addActionListener(listener)
        }
        if (showPromise) {
          row {
            radioButton(IdeBundle.message("subscription.dialog.promise.button"), 1).component.addActionListener(listener)
          }
        }
        row {
          radioButton(IdeBundle.message("subscription.dialog.continue.button"), 2).component.addActionListener(listener)
        }
      }.bind({ selectionButton }, { selectionButton = it })
    }

    return panel
  }

  override fun getOKActionText(): @Nls String {
    return when (selectionButton) {
      0 -> IdeBundle.message("subscription.dialog.activate.ok.text", getPlatformName())
      1 -> IdeBundle.message("subscription.dialog.promise.ok.text")
      else -> IdeBundle.message("subscription.dialog.continue.ok.text")
    }
  }

  override fun getCancelActionText(): @Nls String = IdeBundle.message("subscription.dialog.cancel.button", getApplicationName())

  private fun getApplicationName(): @Nls String = IdeBundle.message(if (PlatformUtils.isPyCharm()) "subscription.dialog.pycharm" else "subscription.dialog.idea")

  private fun getPlatformName(): @Nls String = IdeBundle.message(if (PlatformUtils.isPyCharm()) "subscription.dialog.pro" else "subscription.dialog.ultimate")
}