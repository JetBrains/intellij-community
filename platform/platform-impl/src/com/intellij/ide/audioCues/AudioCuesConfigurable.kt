// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.audioCues

import com.intellij.ide.IdeBundle
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.service
import com.intellij.openapi.observable.util.whenFocusGained
import com.intellij.openapi.options.BackedByPersistentState
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.actionListener
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.ui.layout.selectedValueMatches
import java.awt.event.FocusEvent

internal class AudioCuesConfigurable : BoundConfigurable(IdeBundle.message("configurable.AudioCuesConfigurable.display.name")), BackedByPersistentState {
  override fun getBackingComponents(): Collection<PersistentStateComponent<*>> =
    listOf(service<AudioCuesSettings>())

  override fun createPanel(): DialogPanel = panel {
    val settings = service<AudioCuesSettings>()
    val player = AudioCuePlayer.getInstance()

    row {
      text(IdeBundle.message("audio.cues.description"))
    }.bottomGap(BottomGap.SMALL)

    lateinit var mode: ComboBox<AudioCuesMode>
    row(IdeBundle.message("audio.cues.mode.label")) {
      mode = comboBox(AudioCuesMode.entries, textListCellRenderer("") { it.title })
        .bindItem({ settings.state.mode }, { it?.let(settings::setMode) })
        .component
    }
    indent {
      for (cue in getAudioCues()) {
        row {
          checkBox(cue.title)
            .bindSelected(
              { cue.id !in settings.state.disabledCues },
              { checked -> settings.setCueEnabled(cue, checked) },
            )
            .actionListener { _, _ -> player.preview(cue) }
            .applyToComponent {
              whenFocusGained { e ->
                when (e.cause) {
                  FocusEvent.Cause.TRAVERSAL_FORWARD, FocusEvent.Cause.TRAVERSAL_BACKWARD -> player.preview(cue)
                  else -> {}
                }
              }
            }
        }
      }
    }.enabledIf(mode.selectedValueMatches { it != AudioCuesMode.OFF })
  }
}

internal class AudioCuesConfigurableProvider : ConfigurableProvider() {
  override fun createConfigurable(): Configurable = AudioCuesConfigurable()

  override fun canCreateConfigurable(): Boolean = isAudioCuesFeatureEnabled()
}
