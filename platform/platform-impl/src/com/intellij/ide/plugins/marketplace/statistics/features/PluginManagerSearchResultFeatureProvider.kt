// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.marketplace.statistics.features

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginNode
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.internal.statistic.utils.getPluginInfoByDescriptor
import org.jetbrains.annotations.ApiStatus
import kotlin.math.round

@ApiStatus.Internal
object PluginManagerSearchResultFeatureProvider {
  private val NAME_LENGTH_DATA_KEY = EventFields.Int("nameLength")
  private val DEVELOPED_BY_JETBRAINS_DATA_KEY = EventFields.Boolean("byJetBrains")
  private val ML_SCORE = EventFields.Double("mlScore")

  fun getFeaturesDefinition(): Array<EventField<*>> {
    return arrayOf(
      NAME_LENGTH_DATA_KEY, DEVELOPED_BY_JETBRAINS_DATA_KEY, EventFields.PluginInfo, ML_SCORE,
      *PluginManagerSearchResultMarketplaceFeatureProvider.getFeaturesDefinition(),
      *(MarketplaceTextualFeaturesProvider.getInstanceIfEnabled()?.getFeaturesDefinition() ?: emptyArray())
    )
  }

  fun getSearchStateFeatures(userQuery: String?, descriptor: IdeaPluginDescriptor,
                             pluginToScore: Map<IdeaPluginDescriptor, Double>? = null): List<EventPair<*>> {
    return buildList {
      val pluginInfo = getPluginInfoByDescriptor(descriptor)

      add(NAME_LENGTH_DATA_KEY.with(descriptor.name.length))
      add(DEVELOPED_BY_JETBRAINS_DATA_KEY.with(pluginInfo.isDevelopedByJetBrains()))
      add(EventFields.PluginInfo.with(pluginInfo))
      if (pluginInfo.isSafeToReport() && descriptor is PluginNode) {
        addAll(PluginManagerSearchResultMarketplaceFeatureProvider.getSearchStateFeatures(descriptor))
      }

      if (userQuery != null) {
        MarketplaceTextualFeaturesProvider.getInstanceIfEnabled()
          ?.getTextualFeatures(userQuery, descriptor.name)
          ?.also { addAll(it) }
      }

      pluginToScore?.get(descriptor)?.let {
        add(ML_SCORE.with(roundDouble(it)))
      }
    }
  }

  private fun roundDouble(value: Double) = if (!value.isFinite()) -1.0 else round(value * 100000) / 100000
}