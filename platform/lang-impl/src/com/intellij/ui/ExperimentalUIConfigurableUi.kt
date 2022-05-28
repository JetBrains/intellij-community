// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.SendFeedbackAction
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.options.ConfigurableUi
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

/**
 * @author Konstantin Bulenkov
 */
class ExperimentalUIConfigurableUi: ConfigurableUi<ExperimentalUI> {
  private val ui: DialogPanel = panel {
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

    if (!SystemInfo.isMac) {
      group(IdeBundle.message("new.ui.settings.group.name")) {
        row {
          checkBox(IdeBundle.message("checkbox.main.menu.separate.toolbar"))
            .bindSelected(
              { UISettings.getInstance().separateMainMenu },
              { UISettings.getInstance().separateMainMenu = it })
        }
      }
    }
  }

  override fun reset(settings: ExperimentalUI) = ui.reset()
  override fun isModified(settings: ExperimentalUI) = ui.isModified()
  override fun apply(settings: ExperimentalUI) = ui.apply()
  override fun getComponent(): JComponent = ui
}
