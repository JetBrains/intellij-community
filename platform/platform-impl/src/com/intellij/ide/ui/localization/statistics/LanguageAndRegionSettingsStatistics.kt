// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.localization.statistics

import com.intellij.ide.Region
import com.intellij.internal.statistic.eventLog.events.EventId1
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import java.util.*


internal class LanguageAndRegionSettingsStatistics(private val source: EventSource) : LocalizationStatistics() {

  private val settingsApplied: VarargEventId = localizationActionsGroup.registerVarargEvent("settings.applied", selectedLang, selectedLangPrev, selectedRegion, selectedRegionPrev, eventSource)


  fun settingsUpdated(locale: Locale, localePrev: Locale, region: Region, regionPrev: Region) {
    logEvent(settingsApplied, FieldsListBuilder()
      .with(selectedLang, locale.toLanguageTag())
      .with(selectedLangPrev, localePrev.toLanguageTag())
      .with(selectedRegion, region.externalName())
      .with(selectedRegionPrev, regionPrev.externalName())
      .with(eventSource, source)
      .list())
  }

  override fun logEvent(event: VarargEventId, params: List<EventPair<*>>) {
    event.log(params)
  }

  override fun logEvent(event: EventId1<EventSource>, eventSource: EventSource) {
    event.log(eventSource)
  }

  override fun getSource(): EventSource = source
}