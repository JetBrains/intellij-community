// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.marketplace.statistics.features

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginNode
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.internal.statistic.utils.getPluginInfoByDescriptor

object PluginManagerSearchResultFeatureProvider {
  private val NAME_LENGTH_DATA_KEY = EventFields.Int("nameLength")
  private val DEVELOPED_BY_JETBRAINS_DATA_KEY = EventFields.Boolean("byJetBrains")
  private val MARKETPLACE_INFO_DATA_KEY = ObjectEventField(
    "marketplaceInfo", *PluginManagerSearchResultMarketplaceFeatureProvider.getFeaturesDefinition()
  )

  fun getFeaturesDefinition(): Array<EventField<*>> {
    return arrayOf(
      NAME_LENGTH_DATA_KEY, DEVELOPED_BY_JETBRAINS_DATA_KEY, EventFields.PluginInfo, MARKETPLACE_INFO_DATA_KEY
    )
  }

  fun getSearchStateFeatures(userQuery: String?, descriptor: IdeaPluginDescriptor): List<EventPair<*>> = buildList {
    val pluginInfo = getPluginInfoByDescriptor(descriptor)

    add(NAME_LENGTH_DATA_KEY.with(descriptor.name.length))
    add(DEVELOPED_BY_JETBRAINS_DATA_KEY.with(pluginInfo.isDevelopedByJetBrains()))
    add(EventFields.PluginInfo.with(pluginInfo))
    if (pluginInfo.isSafeToReport() && descriptor is PluginNode) {
      add(MARKETPLACE_INFO_DATA_KEY.with(PluginManagerSearchResultMarketplaceFeatureProvider.getSearchStateFeatures(descriptor)))
    }
  }
}