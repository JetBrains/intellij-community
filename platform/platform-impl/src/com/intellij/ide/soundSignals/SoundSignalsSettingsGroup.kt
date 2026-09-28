// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.soundSignals

import com.intellij.accessibility.AccessibilitySettings
import com.intellij.ide.IdeBundle
import com.intellij.openapi.components.service
import com.intellij.openapi.observable.util.whenFocusGained
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.actionListener
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.ui.layout.selectedValueMatches
import java.awt.event.FocusEvent

internal fun Panel.soundSignalsGroup() {
  if (!isSoundSignalsFeatureEnabled()) return
  val settings = service<AccessibilitySettings>()
  val player = SoundSignalPlayer.getInstance()

  group(IdeBundle.message("sound.signals.group.title")) {
    lateinit var mode: ComboBox<SoundSignalsMode>
    row(IdeBundle.message("sound.signals.mode.label")) {
      mode = comboBox(SoundSignalsMode.entries, textListCellRenderer("") { it.title })
        .bindItem({ settings.state.soundSignals.mode }, { mode -> mode?.let { settings.updateSoundSignals { it.copy(mode = mode) } } })
        .component
    }
    indent {
      for (signal in getSoundSignals()) {
        row {
          checkBox(signal.title)
            .bindSelected(
              { signal.id !in settings.state.soundSignals.disabledSignals },
              { checked -> settings.setSignalEnabled(signal, checked) },
            )
            .actionListener { _, _ -> player.preview(signal) }
            .applyToComponent {
              whenFocusGained { e ->
                when (e.cause) {
                  FocusEvent.Cause.TRAVERSAL_FORWARD, FocusEvent.Cause.TRAVERSAL_BACKWARD -> player.preview(signal)
                  else -> {}
                }
              }
            }
        }
      }
    }.enabledIf(mode.selectedValueMatches { it != SoundSignalsMode.OFF })
  }
}

private fun AccessibilitySettings.setSignalEnabled(signal: SoundSignal, enabled: Boolean) {
  updateSoundSignals {
    it.copy(disabledSignals = if (enabled) it.disabledSignals - signal.id else it.disabledSignals + signal.id)
  }
}

private fun AccessibilitySettings.updateSoundSignals(function: (SoundSignalsSettingsState) -> SoundSignalsSettingsState) {
  update { it.copy(soundSignals = function(it.soundSignals)) }
}
