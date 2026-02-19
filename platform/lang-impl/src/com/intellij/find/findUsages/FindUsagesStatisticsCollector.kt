// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.findUsages

import com.intellij.ide.util.scopeChooser.ScopeIdMapper
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.ObjectEventData
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import com.intellij.usages.impl.ScopeRuleValidator
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object FindUsagesStatisticsCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  @JvmField
  val GROUP: EventLogGroup = EventLogGroup("find.usages", 3)

  const val OPTIONS_EVENT_ID: String = "options"

  private val SEARCHABLE_SCOPE_EVENT_FIELD = EventFields.StringValidatedByCustomRule("searchScope", ScopeRuleValidator::class.java)
  private val SEARCH_FOR_TEXT_OCCURRENCES_FIELD = EventFields.Boolean("isSearchForTextOccurrences")
  private val IS_USAGES_FIELD = EventFields.Boolean("isUsages")
  private val ADDITIONAL = EventFields.createAdditionalDataField(GROUP.id, OPTIONS_EVENT_ID)
  private val OPEN_IN_NEW_TAB = EventFields.Boolean("openInNewTab")

  private val FIND_USAGES_OPTIONS = GROUP.registerVarargEvent(OPTIONS_EVENT_ID,
                                                              SEARCH_FOR_TEXT_OCCURRENCES_FIELD,
                                                              IS_USAGES_FIELD,
                                                              OPEN_IN_NEW_TAB,
                                                              SEARCHABLE_SCOPE_EVENT_FIELD,
                                                              ADDITIONAL)

  @JvmStatic
  fun logOptions(project: Project, options: FindUsagesOptions, openInNewTab: Boolean) {
    val data: MutableList<EventPair<*>> = mutableListOf(
      SEARCH_FOR_TEXT_OCCURRENCES_FIELD.with(options.isSearchForTextOccurrences),
      IS_USAGES_FIELD.with(options.isUsages),
      OPEN_IN_NEW_TAB.with(openInNewTab),
      SEARCHABLE_SCOPE_EVENT_FIELD.with(ScopeIdMapper.instance.getScopeSerializationId(options.searchScope.displayName)),
    )

    if (options is FusAwareFindUsagesOptions) {
      data.add(ADDITIONAL.with(ObjectEventData(options.additionalUsageData)))
    }

    FIND_USAGES_OPTIONS.log(project, *data.toTypedArray())
  }
}