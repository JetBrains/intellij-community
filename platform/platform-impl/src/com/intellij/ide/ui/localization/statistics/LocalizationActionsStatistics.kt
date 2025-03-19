// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.localization.statistics

import com.intellij.diagnostic.LoadingState
import com.intellij.ide.Region
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.internal.statistic.eventLog.events.EventFields.Long
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.application.ApplicationManager
import com.jetbrains.rd.util.collections.SynchronizedList
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.*

@Internal
object LocalizationActionsStatistics: CounterUsagesCollector() {
  private val localizationActionsGroup = EventLogGroup("localization.actions.info", 5)
  private val eventSource: EnumEventField<EventSource> = EventFields.Enum<EventSource>("event_source")
  private val availableLanguages = listOf(Locale.CHINA, Locale.JAPANESE, Locale.KOREAN, Locale.ENGLISH).map { it.toLanguageTag() }
  private val availableRegions = Region.entries.map { it.externalName() }
  private val selectedLang: StringEventField = EventFields.String("selected_language", availableLanguages)
  private val selectedLangPrev: StringEventField = EventFields.String("selected_language_prev", availableLanguages)
  private val selectedRegion: StringEventField = EventFields.String("selected_region", availableRegions)
  private val selectedRegionPrev: StringEventField = EventFields.String("selected_region_prev", availableRegions)
  private val eventTimestamp: EventField<Long> = Long("timestamp")
  private val hyperLinkActivatedEvent: VarargEventId = localizationActionsGroup.registerVarargEvent("documentation_link_activated", eventSource, eventTimestamp)
  private val languageExpandEvent: VarargEventId = localizationActionsGroup.registerVarargEvent("language_expanded", eventSource, eventTimestamp)
  private val languageSelectedEvent: VarargEventId = localizationActionsGroup.registerVarargEvent("language_selected", selectedLang, selectedLangPrev, eventSource, eventTimestamp)
  private val moreLanguagesEvent: VarargEventId = localizationActionsGroup.registerVarargEvent("more_languages_selected", eventSource, eventTimestamp)
  private val regionExpandEvent: VarargEventId = localizationActionsGroup.registerVarargEvent("region_expanded", eventSource, eventTimestamp)
  private val regionSelectedEvent: VarargEventId = localizationActionsGroup.registerVarargEvent("region_selected", selectedRegion, selectedRegionPrev, eventSource, eventTimestamp)

  private val languages = Locale.getISOLanguages().toList()
  private val osLang = EventFields.String("os_language", languages)
  private val countries = Locale.getISOCountries().toList()
  private val osCountry = EventFields.String("os_country", countries)
  private val detectedLang: StringEventField = EventFields.String("detected_language", availableLanguages)
  private val detectedRegion: StringEventField = EventFields.String("detected_region", availableRegions)

  private val nextButtonPressed: VarargEventId = localizationActionsGroup.registerVarargEvent("next_button_pressed", selectedLang, selectedRegion, eventTimestamp, EventFields.DurationMs, eventSource)
  private val dialogClosedWithoutConfirmation: VarargEventId = localizationActionsGroup.registerVarargEvent("dialog_closed_without_confirmation", selectedLang, selectedRegion, eventTimestamp, EventFields.DurationMs, eventSource)
  private val languageAndRegionBeforeEuaShownEvent: VarargEventId = localizationActionsGroup.registerVarargEvent("dialog_shown", osLang, osCountry, detectedLang, detectedRegion, eventTimestamp, eventSource)

  private val settingsApplied: VarargEventId = localizationActionsGroup.registerVarargEvent("settings_applied", selectedLang, selectedLangPrev, selectedRegion, selectedRegionPrev, eventSource, eventTimestamp)
  
  private var initializationStartedTimestamp: Long? = null
  
  override fun getGroup(): EventLogGroup {
    return localizationActionsGroup
  }

  fun logEvent(event: VarargEventId, params: List<EventPair<*>>) {
    if (!LoadingState.APP_READY.isOccurred || ApplicationManager.getApplication() == null) {
      unSentEvents.add(Pair(event, params))
    }
    else {
      event.log(params)
    }
  }

  fun dialogInitializationStarted(osLocale: Locale, predefinedLocale: Locale, predefinedRegion: Region, source: EventSource) {
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

  fun dialogClosedWithoutConfirmation(lang: Locale, region: Region, source: EventSource) {
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

  fun nextButtonPressed(lang: Locale, region: Region, source: EventSource) {
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

  fun languageExpanded(source: EventSource) {
    logEvent(languageExpandEvent, listOf(
      eventSource.with(source),
      eventTimestamp.with(System.currentTimeMillis())
    ))
  }

  fun languageSelected(selected: Locale, prevSelected: Locale, source: EventSource) {
    logEvent(languageSelectedEvent, listOf(
      selectedLang.with( selected.toLanguageTag()),
      selectedLangPrev.with(prevSelected.toLanguageTag()),
      eventSource.with(source),
      eventTimestamp.with(System.currentTimeMillis())
    )
    )
  }

  fun moreLanguagesSelected(source: EventSource) {
    logEvent(moreLanguagesEvent, listOf(
      eventSource.with(source),
      eventTimestamp.with(System.currentTimeMillis())
    ))
  }

  fun regionExpanded(source: EventSource) {
    logEvent(regionExpandEvent, listOf(
      eventSource.with(source),
      eventTimestamp.with(System.currentTimeMillis())
    ))
  }

  fun regionSelected(selected: Region, prevSelected: Region, source: EventSource) {
    logEvent(regionSelectedEvent, listOf(
      selectedRegion.with(selected.externalName()),
      selectedRegionPrev.with(prevSelected.externalName()),
      eventSource.with(source),
      eventTimestamp.with(System.currentTimeMillis())
    )
    )
  }

  fun hyperLinkActivated(source: EventSource) {
    logEvent(hyperLinkActivatedEvent, listOf(
      eventSource.with(source),
      eventTimestamp.with(System.currentTimeMillis())
    ))
  }

  fun settingsUpdated(locale: Locale, localePrev: Locale, region: Region, regionPrev: Region, source: EventSource) {
    logEvent(settingsApplied, listOf(
      selectedLang.with(locale.toLanguageTag()),
      selectedLangPrev.with(localePrev.toLanguageTag()),
      selectedRegion.with(region.externalName()),
      selectedRegionPrev.with(regionPrev.externalName()),
      eventSource.with(source),
      eventTimestamp.with(System.currentTimeMillis())
    )
    )
  }
}

internal val unSentEvents = SynchronizedList<Pair<VarargEventId, List<EventPair<*>>>>()

@Internal
enum class EventSource {
  SETTINGS,
  WELCOME_SCREEN,
  PRE_EUA_DIALOG,
  NOT_SET
}