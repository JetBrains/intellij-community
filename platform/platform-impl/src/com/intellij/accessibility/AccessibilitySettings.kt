// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.accessibility

import com.intellij.ide.soundSignals.SoundSignalsSettingsState
import com.intellij.ide.soundSignals.refreshSoundSignalsState
import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.annotations.Property
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@State(name = "Accessibility", storages = [Storage("accessibility.xml")], category = SettingsCategory.UI)
class AccessibilitySettings : SerializablePersistentStateComponent<AccessibilitySettingsState>(AccessibilitySettingsState()) {
  override fun loadState(state: AccessibilitySettingsState) {
    val before = this.state
    super.loadState(state)
    onChanged(before, state)
  }

  override fun noStateLoaded() {
    loadState(AccessibilitySettingsState())
  }

  internal fun update(function: (AccessibilitySettingsState) -> AccessibilitySettingsState) {
    val before = state
    onChanged(before, updateState(function))
  }

  private fun onChanged(before: AccessibilitySettingsState, after: AccessibilitySettingsState) {
    if (before.soundSignals != after.soundSignals) refreshSoundSignalsState()
  }
}

@ApiStatus.Internal
data class AccessibilitySettingsState(
  @JvmField @Property(surroundWithTag = false) val soundSignals: SoundSignalsSettingsState = SoundSignalsSettingsState(),
)
