// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.marketplace.statistics.features

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.internal.statistic.eventLog.events.*
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object PluginManagerSearchResultsFeatureProvider {
  private const val RESULTS_REPORT_COUNT = 30

  private val IS_EMPTY_DATA_KEY = EventFields.Boolean("isEmpty")
  private val RESULTS_COUNT_DATA_KEY = EventFields.Int("total")
  private val RESULTS_COUNT_LIMIT_DATA_KEY = EventFields.Int("reportLimit")
  private val RESULTS_DATA_KEY = ObjectListEventField(
    "results", *PluginManagerSearchResultFeatureProvider.getFeaturesDefinition()
  )

  fun getFeaturesDefinition(): Array<EventField<*>> {
    return arrayOf(
      IS_EMPTY_DATA_KEY, RESULTS_COUNT_DATA_KEY, RESULTS_COUNT_LIMIT_DATA_KEY, RESULTS_DATA_KEY
    )
  }

  fun getCommonFeatures(userQuery: String?, result: List<IdeaPluginDescriptor>) = arrayListOf<EventPair<*>>(
    IS_EMPTY_DATA_KEY.with(result.isEmpty()),
    RESULTS_COUNT_DATA_KEY.with(result.size),
    RESULTS_COUNT_LIMIT_DATA_KEY.with(RESULTS_REPORT_COUNT)
  )

  fun getSearchStateFeatures(userQuery: String?, result: List<IdeaPluginDescriptor>,
                             pluginToScore: Map<IdeaPluginDescriptor, Double>?): List<EventPair<*>> {
    return getCommonFeatures(userQuery, result).apply {
      add(RESULTS_DATA_KEY.with(result.take(RESULTS_REPORT_COUNT).map {
        ObjectEventData(PluginManagerSearchResultFeatureProvider.getSearchStateFeatures(userQuery, it, pluginToScore))
      }))
    }
  }
}