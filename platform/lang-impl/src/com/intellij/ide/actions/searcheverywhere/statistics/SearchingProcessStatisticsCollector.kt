// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere.statistics

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

internal object SearchingProcessStatisticsCollector : CounterUsagesCollector() {
  private val group = EventLogGroup("search.everywhere.process", 2)

  private val searchStartedEvent = group.registerEvent("contributor.search.started",
                                                       SearchEverywhereUsageTriggerCollector.CONTRIBUTOR_ID_FIELD)
  private val elementFoundEvent = group.registerEvent("first.element.found", SearchEverywhereUsageTriggerCollector.CONTRIBUTOR_ID_FIELD)
  private val elementShownEvent = group.registerEvent("firs.element.shown", SearchEverywhereUsageTriggerCollector.CONTRIBUTOR_ID_FIELD)

  @JvmStatic
  fun searchStarted(contributor: SearchEverywhereContributor<*>) {
    val id = SearchEverywhereUsageTriggerCollector.getReportableContributorID(contributor)
    searchStartedEvent.log(id)
  }

  @JvmStatic
  fun elementFound(contributor: SearchEverywhereContributor<*>) {
    val id = SearchEverywhereUsageTriggerCollector.getReportableContributorID(contributor)
    elementFoundEvent.log(id)
  }

  @JvmStatic
  fun elementShown(contributor: SearchEverywhereContributor<*>) {
    val id = SearchEverywhereUsageTriggerCollector.getReportableContributorID(contributor)
    elementShownEvent.log(id)
  }

  override fun getGroup(): EventLogGroup = SearchingProcessStatisticsCollector.group
}