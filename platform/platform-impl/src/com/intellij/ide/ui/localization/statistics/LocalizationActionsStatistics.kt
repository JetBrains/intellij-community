// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.localization.statistics

import com.intellij.diagnostic.LoadingState
import com.intellij.ide.Region
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.internal.statistic.eventLog.events.EventFields.Long
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.*
import java.util.concurrent.TimeUnit

internal class LocalizationActionsStatistics: CounterUsagesCollector() {
  private val localizationActionsGroup = EventLogGroup("localization.actions.info", 2)
  private val eventSource: NullableEnumEventField<EventSource> = EventFields.NullableEnum<EventSource>("event_source")
  private val selectedLang: StringEventField = EventFields.String("selected_language", Region.entries.map { it.externalName() })
  private val selectedLangPrev: StringEventField = EventFields.String("selected_language_prev", Region.entries.map { it.externalName() })
  private val selectedRegion: StringEventField = EventFields.String("selected_region", Region.entries.map { it.externalName() })
  private val selectedRegionPrev: StringEventField = EventFields.String("selected_region_prev", Region.entries.map { it.externalName() })
  private val eventTimestamp: EventField<Long> = Long("timestamp")
  private val hyperLinkActivatedEvent: EventId1<EventSource> = localizationActionsGroup.registerEvent("documentation_link_activated", eventSource)
  private val languageExpandEvent: EventId1<EventSource> = localizationActionsGroup.registerEvent("language_expanded", eventSource)
  private val languageSelectedEvent: VarargEventId = localizationActionsGroup.registerVarargEvent("language_selected", selectedLang, selectedLangPrev, eventSource)
  private val regionExpandEvent: EventId1<EventSource> = localizationActionsGroup.registerEvent("region_expanded", eventSource)
  private val regionSelectedEvent: VarargEventId = localizationActionsGroup.registerVarargEvent("region_selected", selectedRegion, selectedRegionPrev, eventSource)

  private val languages = Locale.getISOLanguages().toList()
  private val osLang = EventFields.String("os_language", languages)
  private val countries = Locale.getISOCountries().toList()
  private val osCountry = EventFields.String("os_country", countries)
  private val availableLanguages = listOf(Locale.CHINA, Locale.JAPANESE, Locale.KOREAN, Locale.ENGLISH).map { it.toLanguageTag() }
  private val detectedLang: StringEventField = EventFields.String("detected_language", availableLanguages)
  private val detectedRegion: StringEventField = EventFields.String("detected_region", Region.entries.map { it.externalName() })

  private val nextButtonPressed: VarargEventId = localizationActionsGroup.registerVarargEvent("next_button_pressed", selectedLang, selectedRegion, eventTimestamp, EventFields.DurationMs, eventSource)
  private val dialogClosedWithoutConfirmation: VarargEventId = localizationActionsGroup.registerVarargEvent("dialog_closed_without_confirmation", selectedLang, selectedRegion, eventTimestamp, EventFields.DurationMs, eventSource)
  private val languageAndRegionBeforeEuaShownEvent: VarargEventId = localizationActionsGroup.registerVarargEvent("dialog_shown", osLang, osCountry, detectedLang, detectedRegion, eventTimestamp, eventSource)

  private val settingsApplied: VarargEventId = localizationActionsGroup.registerVarargEvent("settings_applied", selectedLang, selectedLangPrev, selectedRegion, selectedRegionPrev, eventSource)
  
  private var initializationStartedTimestamp: Long? = null
  private var source: EventSource = EventSource.NOT_SET
  
  fun setSource(source: EventSource) {
    this.source = source
  }
  
  override fun getGroup(): EventLogGroup {
    return localizationActionsGroup
  }

  fun logEvent(event: VarargEventId, params: List<EventPair<*>>) {
    invokeOnBackgroundWhenApplicationLoaded { event.log(params) }
  }

  fun logEvent(event: EventId1<EventSource>, eventSource: EventSource) {
    invokeOnBackgroundWhenApplicationLoaded { event.log(eventSource) }
  }


  private fun invokeOnBackgroundWhenApplicationLoaded(action: Runnable) {
    if (!LoadingState.APP_READY.isOccurred || ApplicationManager.getApplication() == null) {
      AppExecutorUtil.getAppScheduledExecutorService().schedule({ invokeOnBackgroundWhenApplicationLoaded(action) }, 3000L, TimeUnit.MILLISECONDS)
    }
    else {
      AppExecutorUtil.getAppExecutorService().execute(action)
    }
  }

  fun dialogInitializationStarted(osLocale: Locale, predefinedLocale: Locale, predefinedRegion: Region) {
    initializationStartedTimestamp = System.currentTimeMillis()
    logEvent(languageAndRegionBeforeEuaShownEvent,
             listOf(
               osLang.with(osLocale.language),
               osCountry.with(osLocale.country),
               detectedLang.with(predefinedLocale.toLanguageTag()),
               detectedRegion.with(predefinedRegion.externalName()),
               eventTimestamp.with(initializationStartedTimestamp!!),
               eventSource.with(source)
               )
    )
  }

  fun dialogClosedWithoutConfirmation(lang: Locale, region: Region) {
    val timestamp = System.currentTimeMillis()
    logEvent(dialogClosedWithoutConfirmation,
             listOf(
               selectedLang.with(lang.toLanguageTag()),
               selectedRegion.with(region.externalName()),
               eventTimestamp.with(timestamp),
               EventFields.DurationMs.with(timestamp - initializationStartedTimestamp!!),
               eventSource.with(source)
             )
    )
  }

  fun nextButtonPressed(lang: Locale, region: Region) {
    val timestamp = System.currentTimeMillis()
    logEvent(nextButtonPressed,
             listOf(
               selectedLang.with(lang.toLanguageTag()),
               selectedRegion.with(region.externalName()),
               eventTimestamp.with(timestamp),
               EventFields.DurationMs.with(timestamp - initializationStartedTimestamp!!),
               eventSource.with(source)
             )
    )
  }

  fun languageExpanded() {
    logEvent(languageExpandEvent, source)
  }

  fun languageSelected(selected: Locale, prevSelected: Locale) {
    logEvent(languageSelectedEvent, listOf(
      selectedLang.with( selected.toLanguageTag()),
      selectedLangPrev.with(prevSelected.toLanguageTag()),
      eventSource.with(source)
    )
    )
  }

  fun regionExpanded() {
    logEvent(regionExpandEvent, source)
  }

  fun regionSelected(selected: Region, prevSelected: Region) {
    logEvent(regionSelectedEvent, listOf(
      selectedRegion.with(selected.externalName()),
      selectedRegionPrev.with(prevSelected.externalName()),
      eventSource.with(source)
    )
    )
  }

  fun hyperLinkActivated() {
    logEvent(hyperLinkActivatedEvent, source)
  }

  fun settingsUpdated(locale: Locale, localePrev: Locale, region: Region, regionPrev: Region) {
    logEvent(settingsApplied, listOf(
      selectedLang.with(locale.toLanguageTag()),
      selectedLangPrev.with(localePrev.toLanguageTag()),
      selectedRegion.with(region.externalName()),
      selectedRegionPrev.with(regionPrev.externalName()),
      eventSource.with(source)
    )
    )
  }
}

@Internal
enum class EventSource {
  SETTINGS,
  WELCOME_SCREEN,
  PRE_EUA_DIALOG,
  NOT_SET
}