// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.localization.statistics

import com.intellij.ide.Region
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.internal.statistic.eventLog.events.EventFields.Long
import java.util.*

internal abstract class LocalizationStatistics {
  protected val selectedLang: StringEventField = EventFields.String("selected.language", Region.entries.map { it.externalName() })
  protected val selectedLangPrev: StringEventField = EventFields.String("selected.language.prev", Region.entries.map { it.externalName() })
  protected val selectedRegion: StringEventField = EventFields.String("selected.region", Region.entries.map { it.externalName() })
  protected val selectedRegionPrev: StringEventField = EventFields.String("selected.region.prev", Region.entries.map { it.externalName() })
  protected val eventTimestamp: EventField<Long> = Long("timestamp")
  private fun getHyperLinkActivatedEvent(): EventId = getGroup().registerEvent("documentation.link.activated")
  private fun getLanguageExpandEvent(): EventId = getGroup().registerEvent("language.expanded")
  private fun getLanguageSelectedEvent(): VarargEventId = getGroup().registerVarargEvent("language.selected", selectedLang, selectedLangPrev)
  private fun getRegionExpandEvent(): EventId = getGroup().registerEvent("region.expanded")
  private fun getRegionSelectedEvent(): VarargEventId = getGroup().registerVarargEvent("region.selected", selectedRegion, selectedRegionPrev)

  fun languageExpanded() {
    logEvent(getLanguageExpandEvent())
  }

  fun languageSelected(selected: Locale, prevSelected: Locale) {
    logEvent(getLanguageSelectedEvent(), FieldsListBuilder()
      .with(selectedLang, selected.toLanguageTag())
      .with(selectedLangPrev, prevSelected.toLanguageTag())
      .list())
  }

  fun regionExpanded() {
    logEvent(getRegionExpandEvent())
  }

  fun regionSelected(selected: Region, prevSelected: Region) {
    logEvent(getRegionSelectedEvent(), FieldsListBuilder()
      .with(selectedRegion, selected.externalName())
      .with(selectedRegionPrev, prevSelected.externalName())
      .list())
  }

  fun hyperLinkActivated() {
    logEvent(getHyperLinkActivatedEvent())
  }

  fun registerEvent(group: EventLogGroup, eventId: String, vararg eventFields: EventField<*>): VarargEventId {
    return group.registerVarargEvent(eventId, *eventFields)
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

  abstract fun logEvent(event: EventId)

  abstract fun getGroup(): EventLogGroup
}