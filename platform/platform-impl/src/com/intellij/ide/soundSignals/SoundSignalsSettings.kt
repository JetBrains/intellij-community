// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.soundSignals

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.util.xmlb.annotations.OptionTag
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XMap
import org.jetbrains.annotations.ApiStatus

internal fun isSoundSignalOn(signal: SoundSignal): Boolean {
  if (!isSoundSignalsFeatureEnabled()) return false
  val policy = appliedSoundSignalsPolicy()
  return policy.isPlaySignalsOn && policy.isSignalOn(signal.id)
}

internal fun refreshSoundSignalsState() {
  serviceIfCreated<EditorSoundSignalsManager>()?.updateListenersState()
}

internal fun isSoundSignalsOn(): Boolean = isSoundSignalsFeatureEnabled() && appliedSoundSignalsPolicy().isPlaySignalsOn

internal const val SOUND_SIGNALS_ENABLED_REGISTRY_KEY: String = "ide.sound.signals.enabled"

internal fun isSoundSignalsFeatureEnabled(): Boolean {
  val app = ApplicationManager.getApplication()
  if (app.isHeadlessEnvironment && !app.isUnitTestMode) return false
  return RegistryManager.getInstance().`is`(SOUND_SIGNALS_ENABLED_REGISTRY_KEY)
}

/** The explicit choices. An absent value uses its calculated default, so never write a calculated value here. */
@ApiStatus.Internal
@Tag("soundSignals")
data class SoundSignalsSettingsState(
  @JvmField @OptionTag val playSignals: Boolean? = null,
  @JvmField @XMap(propertyElementName = "signals", entryTagName = "signal", keyAttributeName = "id", valueAttributeName = "enabled")
  val signals: Map<String, Boolean> = emptyMap(),
)
