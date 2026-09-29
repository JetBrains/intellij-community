// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.soundSignals

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

internal class SoundSignalsConfigurable : BoundConfigurable(IdeBundle.message("configurable.SoundSignalsConfigurable.display.name")), BackedByPersistentState {
  override fun getBackingComponents(): Collection<PersistentStateComponent<*>> =
    listOf(service<SoundSignalsSettings>())

  override fun createPanel(): DialogPanel = panel {
    val settings = service<SoundSignalsSettings>()
    val player = SoundSignalPlayer.getInstance()

    row {
      text(IdeBundle.message("sound.signals.description"))
    }.bottomGap(BottomGap.SMALL)

    lateinit var mode: ComboBox<SoundSignalsMode>
    row(IdeBundle.message("sound.signals.mode.label")) {
      mode = comboBox(SoundSignalsMode.entries, textListCellRenderer("") { it.title })
        .bindItem({ settings.state.mode }, { it?.let(settings::setMode) })
        .component
    }
    indent {
      for (signal in getSoundSignals()) {
        row {
          checkBox(signal.title)
            .bindSelected(
              { signal.id !in settings.state.disabledSignals },
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

internal class SoundSignalsConfigurableProvider : ConfigurableProvider() {
  override fun createConfigurable(): Configurable = SoundSignalsConfigurable()

  override fun canCreateConfigurable(): Boolean = isSoundSignalsFeatureEnabled()
}
