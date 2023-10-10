// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.experimental.ExperimentalUiCollector
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.feedback.newUi.NewUIFeedbackDialog
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.builder.Cell
import com.intellij.util.PlatformUtils
import org.jetbrains.annotations.Nls
import javax.swing.JLabel

private const val PROMO_URL = "https://youtu.be/WGwECgPmQ-8"

@Suppress("ExtensionClassShouldBeFinalAndNonPublic")
open class ExperimentalUIConfigurable : BoundSearchableConfigurable(IdeBundle.message("configurable.new.ui.name"),
                                                                    "reference.settings.ide.settings.new.ui") {

  private val EP_NAME: ExtensionPointName<ExperimentalUIConfigurable> = ExtensionPointName.create("com.intellij.newUIConfigurable")

  companion object {
    const val EXPLORE_NEW_UI_URL_TEMPLATE = "https://www.jetbrains.com/%s/new-ui/?utm_source=product&utm_medium=link&utm_campaign=new_ui_release"
  }

  open fun isEnabled(): Boolean = true

  private fun getFirstEnabledConfigurable() = EP_NAME.extensions.firstOrNull { it.isEnabled() }

  final override fun createPanel(): DialogPanel {
    val conf = getFirstEnabledConfigurable()
    if (conf != null) {
      return conf.createPanelInternal()
    }
    return createPanelInternal()
  }

  open fun createPanelInternal(): DialogPanel {
    return panel {
      lateinit var newUiCheckBox: Cell<JBCheckBox>

      row {
        newUiCheckBox = checkBox(IdeBundle.message("checkbox.enable.new.ui"))
          .bindSelected(
            { ExperimentalUI.isNewUI() },
            {
              if (it != ExperimentalUI.isNewUI()) {
                ApplicationManager.getApplication().invokeLater {
                  ExperimentalUiCollector.logSwitchUi(ExperimentalUiCollector.SwitchSource.PREFERENCES, it)
                  ExperimentalUI.setNewUI(it)
                }
              }
            })
          .enabled(
            PlatformUtils.isAqua().or(PlatformUtils.isWriterside()).not()
          ) // the new UI is always enabled for Aqua / WRS and cannot be disabled
      }.comment(IdeBundle.message("ide.restart.required.comment"))

      indent {
        row {
          checkBox(IdeBundle.message("checkbox.compact.mode"))
            .bindSelected(UISettings.getInstance()::compactMode)
            .enabledIf(newUiCheckBox.selected)
            .comment(IdeBundle.message("checkbox.compact.mode.description"))
        }
        if (SystemInfo.isWindows || SystemInfo.isXWindow) {
          row {
            checkBox(IdeBundle.message("checkbox.main.menu.separate.toolbar"))
              .bindSelected(UISettings.getInstance()::separateMainMenu)
              .apply {
                if (SystemInfo.isXWindow) {
                  comment(IdeBundle.message("ide.restart.required.comment"))
                }
              }.enabledIf(newUiCheckBox.selected)
          }
        }
      }

      separator()
        .topGap(TopGap.SMALL)
        .bottomGap(BottomGap.SMALL)

      row {
        icon(AllIcons.Ide.Settings.NewUI)
      }
      row {
        text(IdeBundle.message("new.ui.description"))
      }
      row {
        browserLink(getExploreNewUiLabel(), getExploreNewUiUrl())
        link(IdeBundle.message("new.ui.submit.feedback")) { onSubmitFeedback() }
      }.bottomGap(BottomGap.SMALL)
      if (PlatformUtils.isIntelliJ()) {
        row {
          val img = IconLoader.getIcon("images/newUiPreview.png", this@panel::class.java.classLoader)
          val promo = VideoPromoComponent(JLabel(img), IdeBundle.message("new.ui.watch.new.ui.overview"), alwaysDisplayLabel = true,
                                          darkLabel = true) {
            BrowserUtil.browse(PROMO_URL)
          }
          cell(promo)
        }
      }
    }
  }

  open fun getExploreNewUiUrl(): String = EXPLORE_NEW_UI_URL_TEMPLATE.format("idea")
  open fun getExploreNewUiLabel(): @Nls String = IdeBundle.message("new.ui.explore.new.ui")
  open fun onSubmitFeedback(): Unit = NewUIFeedbackDialog(null, false).show()
  open fun getRedefinedHelpTopic(): String? = null
  open fun onApply() {}

  final override fun getHelpTopic(): String? {
    return getFirstEnabledConfigurable()?.getRedefinedHelpTopic()
  }

  final override fun apply() {
    if (PlatformUtils.isJetBrainsClient()) {
      ExperimentalUI.getInstance().setNewUIInternal(
        /* newUI = */ !ExperimentalUI.isNewUI(),
        /* suggestRestart = */ false
      )
    }
    else {
      getFirstEnabledConfigurable()?.onApply()
      val uiSettingsChanged = isModified
      super.apply()
      if (uiSettingsChanged) {
        LafManager.getInstance().applyDensity()
      }
    }
  }
}
