// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.accessibility

import com.intellij.ide.GeneralSettings
import com.intellij.ide.soundSignals.SoundSignalIdValidationRule
import com.intellij.ide.soundSignals.appliedSoundSignalsPolicy
import com.intellij.ide.soundSignals.findSoundSignal
import com.intellij.ide.soundSignals.isSoundSignalsFeatureEnabled
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.openapi.components.service

internal class AccessibilityStateCollector : ApplicationUsagesCollector() {
  private val group = EventLogGroup("accessibility.state", 5)
  private val screenReaderSupportInVmOptions = group.registerEvent("screen.reader.support.enabled.in.vmoptions", EventFields.Boolean("enabled"))
  private val soundSignalsMode = group.registerEvent("sound.signals.mode", EventFields.Enabled, EventFields.Boolean("explicit"))
  private val soundSignalDisabled =
    group.registerEvent("sound.signal.disabled", EventFields.StringValidatedByCustomRule<SoundSignalIdValidationRule>("signal"))

  override fun getGroup(): EventLogGroup = group

  override fun getMetrics(): Set<MetricEvent> = buildSet {
    System.getProperty(GeneralSettings.SUPPORT_SCREEN_READERS)?.toBoolean()?.let {
      add(screenReaderSupportInVmOptions.metric(it))
    }

    if (!isSoundSignalsFeatureEnabled()) return@buildSet
    val state = service<AccessibilitySettings>().state.soundSignals
    add(soundSignalsMode.metric(appliedSoundSignalsPolicy().isPlaySignalsOn, state.playSignals != null))
    state.signals.filter { (id, enabled) -> !enabled && findSoundSignal(id) != null }.forEach { add(soundSignalDisabled.metric(it.key)) }
  }
}
