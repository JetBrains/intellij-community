// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.soundSignals

import com.intellij.accessibility.AccessibilitySettings
import com.intellij.ide.GeneralSettings
import com.intellij.openapi.components.service

internal class SoundSignalsPolicy(
  private val supportScreenReaders: Boolean,
  private val state: SoundSignalsSettingsState,
) {
  val isPlaySignalsOn: Boolean
    get() = state.playSignals ?: supportScreenReaders

  fun isSignalOn(id: String): Boolean = state.signals[id] ?: true
}

internal fun appliedSoundSignalsPolicy(): SoundSignalsPolicy = SoundSignalsPolicy(
  supportScreenReaders = GeneralSettings.getInstance().isSupportScreenReaders,
  state = service<AccessibilitySettings>().state.soundSignals,
)
