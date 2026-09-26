// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.audioCues

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
@State(name = "AudioCues", storages = [Storage("audiocues.xml")], category = SettingsCategory.UI)
class AudioCuesSettings : SerializablePersistentStateComponent<AudioCuesSettingsState>(AudioCuesSettingsState()) {
  override fun loadState(state: AudioCuesSettingsState) {
    val before = this.state
    super.loadState(state)
    if (before.mode != state.mode) refreshAudioCuesState()
  }

  override fun noStateLoaded() {
    loadState(AudioCuesSettingsState())
  }

  val isEnabled: Boolean
    get() {
      val current = state
      return isAudioCuesFeatureEnabled() && current.mode.isOn
    }

  fun setMode(mode: AudioCuesMode) {
    val before = state
    val after = updateState { it.copy(mode = mode) }
    if (before.mode != after.mode) refreshAudioCuesState()
  }

  fun isCueEnabled(cue: AudioCue): Boolean {
    val current = state
    return isAudioCuesFeatureEnabled() && current.mode.isOn && cue.id !in current.disabledCues
  }

  fun setCueEnabled(cue: AudioCue, enabled: Boolean) {
    updateState {
      it.copy(disabledCues = if (enabled) it.disabledCues - cue.id else it.disabledCues + cue.id)
    }
  }
}

internal fun refreshAudioCuesState() {
  serviceIfCreated<EditorAudioCuesManager>()?.updateListenersState()
}

internal const val AUDIO_CUES_ENABLED_REGISTRY_KEY: String = "ide.audio.cues.enabled"

internal fun isAudioCuesFeatureEnabled(): Boolean {
  val app = ApplicationManager.getApplication()
  if (app.isHeadlessEnvironment && !app.isUnitTestMode) return false
  return RegistryManager.getInstance().`is`(AUDIO_CUES_ENABLED_REGISTRY_KEY)
}

@ApiStatus.Internal
data class AudioCuesSettingsState(
  @JvmField @OptionTag @field:ReportValue val mode: AudioCuesMode = AudioCuesMode.AUTO,
  @JvmField val disabledCues: Set<String> = emptySet(),
)

@ApiStatus.Internal
enum class AudioCuesMode(@param:PropertyKey(resourceBundle = IdeBundle.BUNDLE) private val titleKey: String) {
  AUTO("audio.cues.mode.auto"),
  ON("audio.cues.mode.on"),
  OFF("audio.cues.mode.off"),
  ;

  val title: @Nls String
    get() = IdeBundle.message(titleKey)
}

internal val AudioCuesMode.isOn: Boolean
  get() = when (this) {
    AudioCuesMode.AUTO -> ScreenReader.isActive()
    AudioCuesMode.ON -> true
    AudioCuesMode.OFF -> false
  }
