// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.ide.IdeBundle
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
class SubscriptionExpirationDialog(project: Project?, private val settings: SubscriptionExpirationSettings) :
  LicenseExpirationDialog(project, getImagePath()) {

  private var selectionButton = 0

  enum class ResultState {
    CANCEL,
    ACTIVATE,
    PROMISE,
    EXTEND_TRIAL,
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
    fun show(project: Project?, settings: SubscriptionExpirationSettings): ResultState {
      val dialog = SubscriptionExpirationDialog(project, settings)
      dialog.show()

      if (dialog.exitCode != OK_EXIT_CODE) {
        return ResultState.CANCEL
      }

      return when (dialog.selectionButton) {
        0 -> ResultState.ACTIVATE
        1 -> ResultState.PROMISE
        2 -> ResultState.EXTEND_TRIAL
        else -> ResultState.CONTINUE
      }
    }
  }

  init {
    initDialog(dialogTitle())
  }

  override fun createPanel(): JComponent {
    val panel = panel {
      val platformName = getPlatformName()

      row {
        label(dialogTitle()).component.font = JBFont.h1()
      }
      row {
        browserLink(IdeBundle.message("subscription.dialog.link", platformName), "https://www.jetbrains.com/idea/features")
        bottomGap(BottomGap.MEDIUM)
      }

      buttonsGroup {
        val listener = ActionListener { e ->
          selectionButton = when (e.actionCommand) {
            IdeBundle.message("subscription.dialog.activate.button", platformName) -> 0
            IdeBundle.message("subscription.dialog.promise.button") -> 1
            IdeBundle.message("subscription.dialog.extend.button") -> 2
            else -> 3
          }
          updateOKActionText()
        }

        row {
          radioButton(IdeBundle.message("subscription.dialog.activate.button", platformName), 0).component.addActionListener(listener)
        }
        if (settings.showPromise) {
          row {
            radioButton(IdeBundle.message("subscription.dialog.promise.button"), 1).component.addActionListener(listener)
          }
        }
        if (settings.showExtendTrial) {
          row {
            radioButton(IdeBundle.message("subscription.dialog.extend.button"), 2).component.addActionListener(listener)
          }
        }
        if (settings.showContinueWithoutSubscription) {
          row {
            val cell = radioButton(IdeBundle.message("subscription.dialog.continue.button"), 3)
            cell.component.addActionListener(listener)

            if (settings.showRemDevHint) {
              cell.comment(IdeBundle.message("subscription.dialog.rd.hint"))
            }
          }
        }
      }.bind({ selectionButton }, { selectionButton = it })
    }

    return panel
  }

  override fun getOKActionText(): @Nls String {
    return when (selectionButton) {
      0 -> IdeBundle.message("subscription.dialog.activate.ok.text", getPlatformName())
      1 -> IdeBundle.message("subscription.dialog.promise.ok.text")
      2 -> IdeBundle.message("subscription.dialog.extend.ok.text")
      else -> IdeBundle.message("subscription.dialog.continue.ok.text")
    }
  }

  private fun dialogTitle(): @Nls String {
    val key = if (settings.isEvaluation) "subscription.dialog.title.evaluation" else "subscription.dialog.title.subscription"
    return IdeBundle.message(key, getPlatformName())
  }

  override fun getCancelActionText(): @Nls String = IdeBundle.message("subscription.dialog.cancel.button", getApplicationName())

  private fun getApplicationName(): @Nls String = IdeBundle.message(if (PlatformUtils.isPyCharm()) "subscription.dialog.pycharm" else "subscription.dialog.idea")

  private fun getPlatformName(): @Nls String = IdeBundle.message(if (PlatformUtils.isPyCharm()) "subscription.dialog.pro" else "subscription.dialog.ultimate")
}

@Internal
data class SubscriptionExpirationSettings(
  val isEvaluation: Boolean,
  val showPromise: Boolean,
  val showExtendTrial: Boolean,
  val showContinueWithoutSubscription: Boolean,
  val showRemDevHint: Boolean,
)