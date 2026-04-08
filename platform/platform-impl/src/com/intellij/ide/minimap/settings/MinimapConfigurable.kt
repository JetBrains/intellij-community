// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.settings

import com.intellij.ide.minimap.MinimapUsageCollector
import com.intellij.ide.minimap.utils.MiniMessagesBundle
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.selected

class MinimapConfigurable : BoundConfigurable(MiniMessagesBundle.message("settings.name")) {

  companion object {
    const val ID: String = "com.intellij.minimap"
  }

  private val state = MinimapSettingsState() // todo remove

  override fun createPanel(): DialogPanel = panel {
    MinimapSettings.getInstance().state.let {
      // todo remove
      state.enabled = it.enabled
      state.rightAligned = it.rightAligned
      state.width = it.width
      state.scaleMode = it.scaleMode
    }

    lateinit var enabled: JBCheckBox
    row {
      enabled = checkBox(MiniMessagesBundle.message("settings.enable"))
        .applyToComponent {
          toolTipText = MiniMessagesBundle.message("settings.enable.hint")
        }
        .bindSelected(state::enabled)
        .component
    }
    indent {
      buttonsGroup {
        row(MiniMessagesBundle.message("settings.alignment")) {
          radioButton(MiniMessagesBundle.message("settings.left"), false)
          radioButton(MiniMessagesBundle.message("settings.right"), true)
        }
      }.bind(state::rightAligned)
      buttonsGroup {
        row(MiniMessagesBundle.message("settings.scale")) {
          radioButton(MiniMessagesBundle.message("settings.scale.fill"), MinimapScaleMode.FILL)
          radioButton(MiniMessagesBundle.message("settings.scale.fit"), MinimapScaleMode.FIT)
        }
      }.bind(state::scaleMode)
    }.enabledIf(enabled.selected)
  }

  override fun apply() {
    if (!isModified) {
      return
    }

    super.apply()

    val settings = MinimapSettings.getInstance()
    val currentState = settings.state
    val needToRebuildUI = currentState.rightAligned != state.rightAligned ||
                          currentState.enabled != state.enabled
    if (currentState.enabled != state.enabled) {
      MinimapUsageCollector.logToggled(
        enabled = state.enabled,
        source = MinimapUsageCollector.ToggleSource.SETTINGS,
        scaleMode = state.scaleMode,
        rightAligned = state.rightAligned,
      )
    }

    settings.state = state.copy()
    settings.settingsChangeCallback.notify(if (needToRebuildUI)
                                             MinimapSettings.SettingsChangeType.WithUiRebuild
                                           else
                                             MinimapSettings.SettingsChangeType.Normal)
  }
}
