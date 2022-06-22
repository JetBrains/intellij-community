// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.settings

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTrackerSettings
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTrackerSettings.AutoReloadType.*
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle.message
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.*

class ExternalSystemGroupConfigurable(private val project: Project) : BoundSearchableConfigurable(
  message("settings.build.tools.display.name"),
  "Settings_Build_Tools",
  "build.tools"
) {

  override fun createPanel() = panel {
    val settings = ExternalSystemProjectTrackerSettings.getInstance(project)
    val propertiesComponent = PropertiesComponent.getInstance(project)
    var isEnabled = settings.autoReloadType != NONE
    var value = if (isEnabled) settings.autoReloadType else propertiesComponent.getValue(PREVIOUS_KEY)?.let { enumValueOf(it) } ?: ALL
    lateinit var cbReload: Cell<JBCheckBox>
    row {
      cbReload = checkBox(message("settings.build.tools.auto.reload.radio.button.group.title"))
        .bindSelected({ isEnabled }, { isEnabled = it })
    }
    buttonsGroup(indent = true) {
      row {
        radioButton(message("settings.build.tools.auto.reload.radio.button.all.label"), ALL)
      }
      row {
        radioButton(message("settings.build.tools.auto.reload.radio.button.selective.label"), SELECTIVE)
          .comment(message("settings.build.tools.auto.reload.radio.button.selective.comment"))
      }
    }.bind({ value }, { value = it })
      .enabledIf(cbReload.selected)
    onApply {
      settings.autoReloadType = if (isEnabled) value else NONE
      propertiesComponent.setValue(PREVIOUS_KEY, value.toString())
    }
  }
}

private const val PREVIOUS_KEY = "settings.build.tools.auto.reload"
