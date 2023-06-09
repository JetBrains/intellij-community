// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.feedback.new_ui.dialog.NewUIFeedbackDialog
import com.intellij.ide.IdeBundle
import com.intellij.ide.ProjectWindowCustomizerService
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.builder.Cell
import com.intellij.util.PlatformUtils
import org.jetbrains.annotations.Nls

/**
 * @author Konstantin Bulenkov
 */
open class ExperimentalUIConfigurable : BoundSearchableConfigurable(IdeBundle.message("configurable.new.ui.name"), "reference.settings.ide.settings.new.ui"), Configurable.Beta {

  companion object {
    @JvmStatic
    val EP_NAME: ExtensionPointName<ExperimentalUIConfigurable> = ExtensionPointName.create("com.intellij.newUIConfigurable")
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
            { ExperimentalUI.setNewUI(it) })
          .comment(IdeBundle.message("checkbox.enable.new.ui.description"))
          .enabled(PlatformUtils.isAqua().not()) // the new UI is always enabled for Aqua and cannot be disabled
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

      row { browserLink(getMainChangesAndKnowIssuesLabel(), getMainChangesAndKnownIssuesUrl()) }
        .topGap(TopGap.MEDIUM)
      row { link(IdeBundle.message("new.ui.submit.feedback")) { onSubmitFeedback() } }
    }
  }

  open fun getMainChangesAndKnownIssuesUrl(): String = "https://youtrack.jetbrains.com/articles/IDEA-A-156/Main-changes-and-known-issues"
  open fun getMainChangesAndKnowIssuesLabel(): @Nls String = IdeBundle.message("new.ui.blog.changes.and.issues")
  open fun onSubmitFeedback(): Unit = NewUIFeedbackDialog(null, false).show()
  open fun getRedefinedHelpTopic(): String? = null
  open fun onApply() {}

  final override fun getHelpTopic(): String? {
    return getFirstEnabledConfigurable()?.getRedefinedHelpTopic()
  }

  final override fun apply() {
    getFirstEnabledConfigurable()?.onApply()
    val uiSettingsChanged = isModified
    super.apply()
    if (uiSettingsChanged) {
      LafManager.getInstance().applyDensity()
    }
  }
}
