// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.marketplace.statistics.features

import com.intellij.ide.plugins.PluginNode
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair


internal object PluginManagerSearchResultMarketplaceFeatureProvider {
  private val MARKETPLACE_RATING_DATA_KEY = EventFields.Float("marketplaceRating")
  private val MARKETPLACE_PAID_DATA_KEY = EventFields.Boolean("isPaid")
  private val MARKETPLACE_DOWNLOADS_DATA_KEY = EventFields.Int("downloads")
  private val MARKETPLACE_PLUGIN_ID_DATA_KEY = EventFields.Int("marketplaceId")
  private val MARKETPLACE_PLUGIN_CDATE_DATA_KEY = EventFields.Long("date")
  private val MARKETPLACE_PLUGIN_DAYS_SINCE_LATEST_UPDATE = EventFields.Long("daysSinceLatestUpdate")
  // TODO: add when sent from backend:
  // private val MARKETPLACE_PLUGIN_TAGS = EventFields.StringListValidatedByCustomRule<MarketplaceTagValidator>("pluginTags")
  // private val MARKETPLACE_PLUGIN_HAS_SCREENSHOTS = EventFields.Boolean("hasScreenshots")
  // TODO: add how often plugin gets updated

  private const val MILLIS_IN_DAY = 1000 * 60 * 60 * 24

  fun getFeaturesDefinition(): Array<EventField<*>> {
    return arrayOf(
      MARKETPLACE_RATING_DATA_KEY,
      MARKETPLACE_PAID_DATA_KEY,
      MARKETPLACE_DOWNLOADS_DATA_KEY,
      MARKETPLACE_PLUGIN_ID_DATA_KEY,
      MARKETPLACE_PLUGIN_CDATE_DATA_KEY,
      MARKETPLACE_PLUGIN_DAYS_SINCE_LATEST_UPDATE,
    )
  }

  fun getSearchStateFeatures(pluginNode: PluginNode): List<EventPair<*>> = buildList {
    add(MARKETPLACE_PAID_DATA_KEY.with(pluginNode.isPaid))
    pluginNode.rating?.toFloatOrNull()?.let {
      add(MARKETPLACE_RATING_DATA_KEY.with(it))
    }
    pluginNode.downloads?.toIntOrNull()?.let {
      add(MARKETPLACE_DOWNLOADS_DATA_KEY.with(it))
    }
    pluginNode.externalPluginId?.toIntOrNull()?.let {
      add(MARKETPLACE_PLUGIN_ID_DATA_KEY.with(it))
    }
    if (pluginNode.date != Long.MAX_VALUE) {
      add(MARKETPLACE_PLUGIN_CDATE_DATA_KEY.with(pluginNode.date))
      add(MARKETPLACE_PLUGIN_DAYS_SINCE_LATEST_UPDATE.with((System.currentTimeMillis() - pluginNode.date) / MILLIS_IN_DAY))
    }
  }
}