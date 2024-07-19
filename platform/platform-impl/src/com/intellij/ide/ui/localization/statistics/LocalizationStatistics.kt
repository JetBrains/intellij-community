// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.localization.statistics

import com.intellij.ide.Region
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.internal.statistic.eventLog.events.EventFields.Long
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.*

internal abstract class LocalizationStatistics {
  protected val localizationActionsGroup = EventLogGroup("localization.actions.info", 1)
  protected val eventSource: EnumEventField<EventSource> = EventFields.Enum("event.source", EventSource::class.java)
  protected val selectedLang: StringEventField = EventFields.String("selected.language", Region.entries.map { it.externalName() })
  protected val selectedLangPrev: StringEventField = EventFields.String("selected.language.prev", Region.entries.map { it.externalName() })
  protected val selectedRegion: StringEventField = EventFields.String("selected.region", Region.entries.map { it.externalName() })
  protected val selectedRegionPrev: StringEventField = EventFields.String("selected.region.prev", Region.entries.map { it.externalName() })
  protected val eventTimestamp: EventField<Long> = Long("timestamp")
  private fun getHyperLinkActivatedEvent(): EventId1<EventSource> = localizationActionsGroup.registerEvent("documentation.link.activated", eventSource)
  private fun getLanguageExpandEvent(): EventId1<EventSource> = localizationActionsGroup.registerEvent("language.expanded", eventSource)
  private fun getLanguageSelectedEvent(): VarargEventId = localizationActionsGroup.registerVarargEvent("language.selected", selectedLang, selectedLangPrev, eventSource)
  private fun getRegionExpandEvent(): EventId1<EventSource> = localizationActionsGroup.registerEvent("region.expanded", eventSource)
  private fun getRegionSelectedEvent(): VarargEventId = localizationActionsGroup.registerVarargEvent("region.selected", selectedRegion, selectedRegionPrev, eventSource)
  
  fun languageExpanded() {
    logEvent(getLanguageExpandEvent(), getSource())
  }

  fun languageSelected(selected: Locale, prevSelected: Locale) {
    logEvent(getLanguageSelectedEvent(), FieldsListBuilder()
      .with(selectedLang, selected.toLanguageTag())
      .with(selectedLangPrev, prevSelected.toLanguageTag())
      .with(eventSource, getSource())
      .list())
  }

  fun regionExpanded() {
    logEvent(getRegionExpandEvent(), getSource())
  }

  fun regionSelected(selected: Region, prevSelected: Region) {
    logEvent(getRegionSelectedEvent(), FieldsListBuilder()
      .with(selectedRegion, selected.externalName())
      .with(selectedRegionPrev, prevSelected.externalName())
      .with(eventSource, getSource())
      .list())
  }

  fun hyperLinkActivated() {
    logEvent(getHyperLinkActivatedEvent(), getSource())
  }

  protected class FieldsListBuilder {
    private val list: MutableList<EventPair<*>> = ArrayList()

    fun list(): List<EventPair<*>> {
      return list
    }

    fun <T> with(field: EventField<T>, value: T?): FieldsListBuilder {
      if (value != null) {
        list.add(field.with(value))
      }

      return this
    }
  }

  abstract fun logEvent(event: VarargEventId, params: List<EventPair<*>>)

  abstract fun logEvent(event: EventId1<EventSource>, eventSource: EventSource)
  
  abstract fun getSource(): EventSource
}

@Internal
enum class EventSource {
  SETTINGS,
  WELCOME_SCREEN,
  PRE_EUA_DIALOG
}