// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.soundSignals

import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ReportValue
import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.util.ui.accessibility.ScreenReader
import com.intellij.util.xmlb.annotations.OptionTag
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

@ApiStatus.Internal
@State(name = "SoundSignals", storages = [Storage("soundSignals.xml")], category = SettingsCategory.UI)
class SoundSignalsSettings : SerializablePersistentStateComponent<SoundSignalsSettingsState>(SoundSignalsSettingsState()) {
  override fun loadState(state: SoundSignalsSettingsState) {
    val before = this.state
    super.loadState(state)
    if (before.mode != state.mode) refreshSoundSignalsState()
  }

  override fun noStateLoaded() {
    loadState(SoundSignalsSettingsState())
  }

  val isEnabled: Boolean
    get() {
      val current = state
      return isSoundSignalsFeatureEnabled() && current.mode.isOn
    }

  fun setMode(mode: SoundSignalsMode) {
    val before = state
    val after = updateState { it.copy(mode = mode) }
    if (before.mode != after.mode) refreshSoundSignalsState()
  }

  fun isSignalEnabled(signal: SoundSignal): Boolean {
    val current = state
    return isSoundSignalsFeatureEnabled() && current.mode.isOn && signal.id !in current.disabledSignals
  }

  fun setSignalEnabled(signal: SoundSignal, enabled: Boolean) {
    updateState {
      it.copy(disabledSignals = if (enabled) it.disabledSignals - signal.id else it.disabledSignals + signal.id)
    }
  }
}

internal fun refreshSoundSignalsState() {
  serviceIfCreated<EditorSoundSignalsManager>()?.updateListenersState()
}

internal const val SOUND_SIGNALS_ENABLED_REGISTRY_KEY: String = "ide.sound.signals.enabled"

internal fun isSoundSignalsFeatureEnabled(): Boolean {
  val app = ApplicationManager.getApplication()
  if (app.isHeadlessEnvironment && !app.isUnitTestMode) return false
  return RegistryManager.getInstance().`is`(SOUND_SIGNALS_ENABLED_REGISTRY_KEY)
}

@ApiStatus.Internal
data class SoundSignalsSettingsState(
  @JvmField @OptionTag @field:ReportValue val mode: SoundSignalsMode = SoundSignalsMode.AUTO,
  @JvmField val disabledSignals: Set<String> = emptySet(),
)

@ApiStatus.Internal
enum class SoundSignalsMode(@param:PropertyKey(resourceBundle = IdeBundle.BUNDLE) private val titleKey: String) {
  AUTO("sound.signals.mode.auto"),
  ON("sound.signals.mode.on"),
  OFF("sound.signals.mode.off"),
  ;

  val title: @Nls String
    get() = IdeBundle.message(titleKey)
}

internal val SoundSignalsMode.isOn: Boolean
  get() = when (this) {
    SoundSignalsMode.AUTO -> ScreenReader.isActive()
    SoundSignalsMode.ON -> true
    SoundSignalsMode.OFF -> false
  }
