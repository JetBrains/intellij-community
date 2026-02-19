// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.marketplace.statistics.features

import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.utils.getPluginInfoByDescriptor
import com.intellij.openapi.util.IntellijInternalApi
import org.jetbrains.annotations.ApiStatus
import kotlin.math.round

@ApiStatus.Internal
@IntellijInternalApi
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

  fun getSearchStateFeatures(userQuery: String?, descriptor: PluginUiModel,
                             pluginToScore: Map<PluginUiModel, Double>? = null): List<EventPair<*>> {
    return buildList {
      val pluginInfo = getPluginInfoByDescriptor(descriptor.getDescriptor())

      add(NAME_LENGTH_DATA_KEY.with(descriptor.name!!.length))
      add(DEVELOPED_BY_JETBRAINS_DATA_KEY.with(pluginInfo.isDevelopedByJetBrains()))
      add(EventFields.PluginInfo.with(pluginInfo))
      if (pluginInfo.isSafeToReport() && descriptor.isFromMarketplace) {
        addAll(PluginManagerSearchResultMarketplaceFeatureProvider.getSearchStateFeatures(descriptor))
      }

      if (userQuery != null) {
        MarketplaceTextualFeaturesProvider.getInstanceIfEnabled()
          ?.getTextualFeatures(userQuery, descriptor.name!!)
          ?.also { addAll(it) }
      }

      pluginToScore?.get(descriptor)?.let {
        add(ML_SCORE.with(roundDouble(it)))
      }
    }
  }

  private fun roundDouble(value: Double) = if (!value.isFinite()) -1.0 else round(value * 100000) / 100000
}