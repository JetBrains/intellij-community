// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.marketplace.statistics.collectors

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.marketplace.statistics.features.PluginManagerLocalSearchFeatureProvider
import com.intellij.ide.plugins.marketplace.statistics.features.PluginManagerMarketplaceSearchFeatureProvider
import com.intellij.ide.plugins.marketplace.statistics.features.PluginManagerSearchResultsFeatureProvider
import com.intellij.ide.plugins.marketplace.statistics.features.PluginManagerUserQueryFeatureProvider
import com.intellij.ide.plugins.newui.SearchQueryParser
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.ObjectEventData
import com.intellij.internal.statistic.eventLog.events.ObjectEventField
import com.intellij.internal.statistic.eventLog.mp.MP_RECORDER_ID
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus


private const val PM_MP_GROUP_ID = "mp.$PM_FUS_GROUP_ID"
private const val PM_MP_GROUP_VERSION = 1
private val EVENT_GROUP = EventLogGroup(
  PM_MP_GROUP_ID,
  // this is needed to be able to change `PM_MP_GROUP_ID` child group without a requirement to update `PM_FUS_GROUP_ID` parent group version.
  PM_FUS_GROUP_VERSION + PM_MP_GROUP_VERSION,
  MP_RECORDER_ID
)

@ApiStatus.Internal
class PluginManagerMPCollector : PluginManagerFUSCollector() {
  override fun getGroup(): EventLogGroup = EVENT_GROUP

  // Search
  private val USER_QUERY_FEATURES_DATA_KEY = ObjectEventField(
    "userQueryFeatures", *PluginManagerUserQueryFeatureProvider.getFeaturesDefinition()
  )
  private val MARKETPLACE_SEARCH_FEATURES_DATA_KEY = ObjectEventField(
    "marketplaceSearchFeatures", *PluginManagerMarketplaceSearchFeatureProvider.getFeaturesDefinition()
  )
  private val LOCAL_SEARCH_FEATURES_DATA_KEY = ObjectEventField(
    "localSearchFeatures", *PluginManagerLocalSearchFeatureProvider.getFeaturesDefinition()
  )
  private val SEARCH_RESULTS_FEATURES_DATA_KEY = ObjectEventField(
    "resultsFeatures", *PluginManagerSearchResultsFeatureProvider.getFeaturesDefinition()
  )

  private val MARKETPLACE_TAB_SEARCH_PERFORMED = group.registerVarargEvent(
    "marketplace.tab.search", USER_QUERY_FEATURES_DATA_KEY, MARKETPLACE_SEARCH_FEATURES_DATA_KEY, SEARCH_RESULTS_FEATURES_DATA_KEY
  )
  private val INSTALLED_TAB_SEARCH_PERFORMED = group.registerVarargEvent(
    "installed.tab.search", USER_QUERY_FEATURES_DATA_KEY, LOCAL_SEARCH_FEATURES_DATA_KEY, SEARCH_RESULTS_FEATURES_DATA_KEY
  )
  private val SEARCH_RESET = group.registerEvent("search.reset")

  fun performMarketplaceSearch(project: Project?, query: SearchQueryParser.Marketplace, results: List<IdeaPluginDescriptor>) {
    MARKETPLACE_TAB_SEARCH_PERFORMED.getIfInitializedOrNull()?.log(project) {
      add(USER_QUERY_FEATURES_DATA_KEY.with(ObjectEventData(
        PluginManagerUserQueryFeatureProvider.getSearchStateFeatures(query.searchQuery)
      )))
      add(MARKETPLACE_SEARCH_FEATURES_DATA_KEY.with(ObjectEventData(
        PluginManagerMarketplaceSearchFeatureProvider.getSearchStateFeatures(query)
      )))
      add(SEARCH_RESULTS_FEATURES_DATA_KEY.with(ObjectEventData(
        PluginManagerSearchResultsFeatureProvider.getSearchStateFeatures(query.searchQuery, results)
      )))
    }
  }

  fun performInstalledTabSearch(project: Project?, query: SearchQueryParser.Installed, results: List<IdeaPluginDescriptor>) {
    INSTALLED_TAB_SEARCH_PERFORMED.getIfInitializedOrNull()?.log(project) {
      add(USER_QUERY_FEATURES_DATA_KEY.with(ObjectEventData(
        PluginManagerUserQueryFeatureProvider.getSearchStateFeatures(query.searchQuery)
      )))
      add(LOCAL_SEARCH_FEATURES_DATA_KEY.with(ObjectEventData(
        PluginManagerLocalSearchFeatureProvider.getSearchStateFeatures(query)
      )))
      add(SEARCH_RESULTS_FEATURES_DATA_KEY.with(ObjectEventData(
        PluginManagerSearchResultsFeatureProvider.getSearchStateFeatures(query.searchQuery, results)
      )))
    }
  }

  fun searchReset() {
    SEARCH_RESET.log()
  }
}