// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.soundSignals

import com.intellij.accessibility.AccessibilitySettings
import com.intellij.ide.GeneralSettings
import com.intellij.openapi.components.service

/** A signal without a choice follows the screen reader support. */
internal class SoundSignalsPolicy(
  private val supportScreenReaders: Boolean,
  private val state: SoundSignalsSettingsState,
) {
  fun isSignalOn(id: String): Boolean = state.signals[id] ?: supportScreenReaders
}

internal fun appliedSoundSignalsPolicy(): SoundSignalsPolicy = SoundSignalsPolicy(
  supportScreenReaders = GeneralSettings.getInstance().isSupportScreenReaders,
  state = service<AccessibilitySettings>().state.soundSignals,
)
