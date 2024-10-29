// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus

import com.intellij.ide.Region
import com.intellij.ide.RegionSettings
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.StringEventField
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.l10n.LocalizationUtil
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class LocalizationUsageCollector : ApplicationUsagesCollector() {
  private val LOCALES = listOf("am", "ar", "as", "az", "bn", "cs", "da", "de", "el", "en", "es", "fa", "fr", "gu",
                               "ha", "hi", "hu", "ig", "in", "it", "ja", "kk", "kn", "ko", "ml", "mr", "my", "nb",
                               "ne", "nl", "nn", "no", "or", "pa", "pl", "pt", "ro", "ru", "rw", "sd", "si", "so",
                               "sv", "ta", "te", "th", "tr", "uk", "ur", "uz", "vi", "yo", "zh", "zh-CN", "zu", "other")

  private val GROUP = EventLogGroup("localization.info", 5)
  private val selectedLanguage = GROUP.registerEvent("selected.language", StringEventField.ValidatedByAllowedValues("value", LOCALES))
  private val selectedRegion = GROUP.registerEvent("selected.region", EventFields.Enum("value", Region::class.java))

  override fun getMetrics(): Set<MetricEvent> {
    val result = mutableSetOf<MetricEvent>()

    val locale = LocalizationUtil.getLocale()
    val language = if (LOCALES.contains(locale.toLanguageTag())) {
      locale.toLanguageTag()
    }
    else if (LOCALES.contains(locale.language)) {
      locale.language
    }
    else "other"
    result.add(selectedLanguage.metric(language))

    val region = RegionSettings.getRegion()
    result.add(selectedRegion.metric(region))
    return result
  }

  override fun getGroup(): EventLogGroup {
    return GROUP
  }
}