// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.soundSignals

import com.intellij.accessibility.AccessibilitySettings
import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ReportValue
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.util.ui.accessibility.ScreenReader
import com.intellij.util.xmlb.annotations.OptionTag
import com.intellij.util.xmlb.annotations.Tag
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

internal fun isSoundSignalsOn(): Boolean = isSoundSignalsFeatureEnabled() && service<AccessibilitySettings>().state.soundSignals.mode.isOn

internal fun isSoundSignalOn(signal: SoundSignal): Boolean {
  if (!isSoundSignalsFeatureEnabled()) return false
  val state = service<AccessibilitySettings>().state.soundSignals
  return state.mode.isOn && signal.id !in state.disabledSignals
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
@Tag("soundSignals")
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
