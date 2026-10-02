// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.soundSignals

import com.intellij.accessibility.AccessibilitySettings
import com.intellij.ide.GeneralSettings
import com.intellij.openapi.components.service

internal class PendingSoundSignals(private val screenReader: () -> Boolean = { GeneralSettings.getInstance().isSupportScreenReaders }) {
  private val settings = service<AccessibilitySettings>()
  private val views = ArrayList<(SoundSignalsPolicy) -> Unit>()
  private var choices = emptyMap<String, Boolean>()

  private val state: SoundSignalsSettingsState
    get() = settings.state.soundSignals.let { it.copy(signals = it.signals + choices) }

  fun policy(): SoundSignalsPolicy = SoundSignalsPolicy(screenReader(), state)

  fun view(render: (SoundSignalsPolicy) -> Unit) {
    views += render
  }

  /** Also call it when the page shows: the dialog does not reset an unmodified page after another page applies. */
  fun render() {
    val policy = policy()
    for (view in views) view(policy)
  }

  fun choose(choices: Map<String, Boolean>) {
    this.choices += choices
    render()
  }

  fun isModified(): Boolean = state != settings.state.soundSignals

  fun reset() {
    choices = emptyMap()
    render()
  }

  /** Merges, so it keeps what the other page applied. */
  fun apply() {
    val applied = choices.ifEmpty { return }
    choices = emptyMap()
    settings.update { it.copy(soundSignals = it.soundSignals.copy(signals = it.soundSignals.signals + applied)) }
  }
}
