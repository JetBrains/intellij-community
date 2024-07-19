// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.localization.statistics

import com.intellij.diagnostic.LoadingState
import com.intellij.ide.Region
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.*
import java.util.concurrent.TimeUnit

internal class LanguageRegionBeforeEuaStatistics : LocalizationStatistics() {
  private val languages = Locale.getISOLanguages().toList()
  private val osLang = EventFields.String("os.language", languages)

  private val countries = Locale.getISOCountries().toList()
  private val osCountry = EventFields.String("os.country", countries)

  private val availableLanguages = listOf(Locale.CHINA, Locale.JAPANESE, Locale.KOREAN, Locale.ENGLISH).map { it.toLanguageTag() }
  private val detectedLang: StringEventField = EventFields.String("detected.language", availableLanguages)
  private val detectedRegion: StringEventField = EventFields.String("detected.region", Region.entries.map { it.externalName() })

  private val nextButtonPressed: VarargEventId = localizationActionsGroup.registerVarargEvent("next.button.pressed", selectedLang, selectedRegion, eventTimestamp, EventFields.DurationMs, eventSource)
  private val dialogClosedWithoutConfirmation: VarargEventId = localizationActionsGroup.registerVarargEvent("dialog.closed.without.confirmation", selectedLang, selectedRegion, eventTimestamp, EventFields.DurationMs, eventSource)
  private val languageAndRegionBeforeEuaShownEvent: VarargEventId = localizationActionsGroup.registerVarargEvent("language.and.region.dialog.shown", osLang, osCountry, detectedLang, detectedRegion, eventTimestamp, eventSource)

  private var initializationStartedTimestamp: Long? = null

  override fun logEvent(event: VarargEventId, params: List<EventPair<*>>) {
    invokeOnBackgroundWhenApplicationLoaded { event.log(params) }
  }

  override fun logEvent(event: EventId1<EventSource>, eventSource: EventSource) {
    invokeOnBackgroundWhenApplicationLoaded { event.log(eventSource) }
  }

  override fun getSource(): EventSource = EventSource.PRE_EUA_DIALOG


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
             FieldsListBuilder()
               .with(osLang, osLocale.language)
               .with(osCountry, osLocale.country)
               .with(detectedLang, predefinedLocale.toLanguageTag())
               .with(detectedRegion, predefinedRegion.externalName())
               .with(eventTimestamp, initializationStartedTimestamp)
               .with(eventSource, getSource())
               .list())
  }

  fun dialogClosedWithoutConfirmation(lang: Locale, region: Region) {
    val timestamp = System.currentTimeMillis()
    logEvent(dialogClosedWithoutConfirmation,
             FieldsListBuilder()
               .with(selectedLang, lang.toLanguageTag())
               .with(selectedRegion, region.externalName())
               .with(eventTimestamp, timestamp)
               .with(EventFields.DurationMs, timestamp - initializationStartedTimestamp!!)
               .with(eventSource, getSource())
               .list())
  }

  fun nextButtonPressed(lang: Locale, region: Region) {
    val timestamp = System.currentTimeMillis()
    logEvent(nextButtonPressed,
             FieldsListBuilder()
               .with(selectedLang, lang.toLanguageTag())
               .with(selectedRegion, region.externalName())
               .with(eventTimestamp, timestamp)
               .with(EventFields.DurationMs, timestamp - initializationStartedTimestamp!!)
               .with(eventSource, getSource())
               .list())
  }
}