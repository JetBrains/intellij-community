// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.SendFeedbackAction
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel

/**
 * @author Konstantin Bulenkov
 */
internal class ExperimentalUIConfigurable : BoundSearchableConfigurable(
  IdeBundle.message("configurable.new.ui.name"),
  "reference.settings.ide.settings.new.ui") {

  override fun createPanel() = panel {
    row {
      checkBox(IdeBundle.message("checkbox.enable.new.ui"))
        .bindSelected(
          { ExperimentalUI.isNewUI() },
          { Registry.get("ide.experimental.ui").setValue(it) })
        .comment(IdeBundle.message("checkbox.enable.new.ui.description"))
    }

    row { browserLink(IdeBundle.message("new.ui.blog.post.link"), "https://blog.jetbrains.com/idea/2022/05/take-part-in-the-new-ui-preview-for-your-jetbrains-ide/") }
    row { browserLink(IdeBundle.message("new.ui.blog.changes.and.issues"), "https://youtrack.jetbrains.com/articles/IDEA-A-156/Main-changes-and-known-issues") }
    row { link(IdeBundle.message("new.ui.submit.feedback")) { SendFeedbackAction.submit(null) } }

    if (SystemInfo.isWindows || SystemInfo.isXWindow) {
      group(IdeBundle.message("new.ui.settings.group.name")) {
        row {
          checkBox(IdeBundle.message("checkbox.main.menu.separate.toolbar"))
            .bindSelected(UISettings.getInstance()::separateMainMenu)
            .apply {
              if (SystemInfo.isXWindow) {
                comment(IdeBundle.message("ide.restart.required.comment"))
              }
            }
        }
      }
    }
  }

  override fun getHelpTopic(): String? {
    return null
  }

  override fun apply() {
    val uiSettingsChanged = isModified
    super.apply()
    if (uiSettingsChanged) {
      UISettings.getInstance().fireUISettingsChanged()
    }
  }
}
