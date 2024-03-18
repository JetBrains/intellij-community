// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.marketplace.statistics.features

import com.intellij.ide.plugins.PluginNode
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.ObjectEventData


object PluginManagerSearchResultMarketplaceFeatureProvider {
  private val MARKETPLACE_RATING_DATA_KEY = EventFields.Float("marketplaceRating")
  private val MARKETPLACE_PAID_DATA_KEY = EventFields.Boolean("isPaid")
  private val MARKETPLACE_DOWNLOADS_DATA_KEY = EventFields.Int("downloads")
  private val MARKETPLACE_PLUGIN_ID_DATA_KEY = EventFields.Int("marketplaceId")
  private val MARKETPLACE_PLUGIN_CDATE_DATA_KEY = EventFields.Long("date")

  fun getFeaturesDefinition(): Array<EventField<*>> {
    return arrayOf(
      MARKETPLACE_RATING_DATA_KEY,
      MARKETPLACE_PAID_DATA_KEY,
      MARKETPLACE_DOWNLOADS_DATA_KEY,
      MARKETPLACE_PLUGIN_ID_DATA_KEY,
      MARKETPLACE_PLUGIN_CDATE_DATA_KEY
    )
  }

  fun getSearchStateFeatures(pluginNode: PluginNode): ObjectEventData = ObjectEventData(buildList {
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
    add(MARKETPLACE_PLUGIN_CDATE_DATA_KEY.with(pluginNode.date))
  })
}