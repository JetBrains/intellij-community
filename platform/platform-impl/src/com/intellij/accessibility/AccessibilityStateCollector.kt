// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.accessibility

import com.intellij.ide.GeneralSettings
import com.intellij.ide.audioCues.AudioCueIdValidationRule
import com.intellij.ide.audioCues.AudioCuesMode
import com.intellij.ide.audioCues.AudioCuesSettings
import com.intellij.ide.audioCues.findAudioCue
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.openapi.components.service

internal class AccessibilityStateCollector : ApplicationUsagesCollector() {
  private val group = EventLogGroup("accessibility.state", 3)
  private val screenReaderSupportInVmOptions = group.registerEvent("screen.reader.support.enabled.in.vmoptions", EventFields.Boolean("enabled"))
  private val audioCuesMode = group.registerEvent("audio.cues.mode", EventFields.Enum<AudioCuesMode>("mode"))
  private val audioCueDisabled =
    group.registerEvent("audio.cue.disabled", EventFields.StringValidatedByCustomRule<AudioCueIdValidationRule>("cue"))

  override fun getGroup(): EventLogGroup = group

  override fun getMetrics(): Set<MetricEvent> = buildSet {
    System.getProperty(GeneralSettings.SUPPORT_SCREEN_READERS)?.toBoolean()?.let {
      add(screenReaderSupportInVmOptions.metric(it))
    }

    val cues = service<AudioCuesSettings>().state
    add(audioCuesMode.metric(cues.mode))
    cues.disabledCues.filter { findAudioCue(it) != null }.forEach { add(audioCueDisabled.metric(it)) }
  }
}
