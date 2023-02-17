// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.feedback.new_ui.dialog.NewUIFeedbackDialog
import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected

/**
 * @author Konstantin Bulenkov
 */
internal class ExperimentalUIConfigurable : BoundSearchableConfigurable(
  IdeBundle.message("configurable.new.ui.name"),
  "reference.settings.ide.settings.new.ui"), Configurable.Beta {

  override fun createPanel(): DialogPanel {
    //Iterate over ExperimentalUIConfigurableEP and if at least one record exists
    //and enabled, then create and return DialogPanel from it
    //Otherwise return IJ's panel
    return panel {
      lateinit var newUiCheckBox: Cell<JBCheckBox>

      row {
        newUiCheckBox = checkBox(IdeBundle.message("checkbox.enable.new.ui"))
          .bindSelected(
            { ExperimentalUI.isNewUI() },
            { ExperimentalUI.setNewUI(it) })
          .comment(IdeBundle.message("checkbox.enable.new.ui.description"))
      }

      indent {
        row {
          checkBox(IdeBundle.message("checkbox.compact.mode"))
            .bindSelected(UISettings.getInstance()::compactMode)
            .enabledIf(newUiCheckBox.selected)
          comment(IdeBundle.message("checkbox.compact.mode.description"))
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

      row { browserLink(IdeBundle.message("new.ui.blog.changes.and.issues"), "https://youtrack.jetbrains.com/articles/IDEA-A-156/Main-changes-and-known-issues") }
      row { link(IdeBundle.message("new.ui.submit.feedback")) { NewUIFeedbackDialog(null, false).show() } }
    }
  }

  override fun getHelpTopic(): String? {
    return null
  }

  override fun apply() {
    val uiSettingsChanged = isModified
    super.apply()
    if (uiSettingsChanged) {
      LafManager.getInstance().applyDensity()
    }
  }
}
