// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.marketplace.statistics.features

import com.intellij.ide.plugins.enums.SortBy
import com.intellij.ide.plugins.marketplace.ranking.MarketplaceLocalRanker
import com.intellij.ide.plugins.marketplace.statistics.validators.MarketplaceTagValidator
import com.intellij.ide.plugins.marketplace.statistics.validators.MarketplaceVendorsListValidator
import com.intellij.ide.plugins.marketplace.utils.MarketplaceUrls
import com.intellij.ide.plugins.newui.SearchQueryParser
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object PluginManagerMarketplaceSearchFeatureProvider {
  private val IS_SUGGESTED_DATA_KEY = EventFields.Boolean("isSuggested")
  private val IS_STAFF_PICKS_DATA_KEY = EventFields.Boolean("isStaffPicks")
  private val CUSTOM_REPOSITORY_COUNT_DATA_KEY = EventFields.Int("customRepositoryCount")
  private val MARKETPLACE_CUSTOM_REPOSITORY_COUNT_DATA_KEY = EventFields.Int("marketplaceCustomRepositoryCount")
  private val SORT_BY_DATA_KEY = EventFields.Enum<SortBy>("sortBy")
  private val VENDORS_LIST_FILTER_DATA_KEY = EventFields.StringListValidatedByCustomRule(
    "vendorsListFilter", MarketplaceVendorsListValidator::class.java
  )
  private val TAGS_LIST_FILTER_DATA_KEY = EventFields.StringListValidatedByCustomRule(
    "tagsListFilter", MarketplaceTagValidator::class.java
  )
  private val IS_ORDERED_BY_ML = EventFields.Boolean("isOrderedByML")
  private val ML_EXPERIMENT_GROUP = EventFields.Int("experimentGroup")
  private val ML_EXPERIMENT_VERSION = EventFields.Int("experimentVersion")
  private val IS_USER_INTERNAL = EventFields.Boolean("isUserInternal")

  fun getFeaturesDefinition(): Array<EventField<*>> {
    return arrayOf(
      IS_SUGGESTED_DATA_KEY, IS_STAFF_PICKS_DATA_KEY, CUSTOM_REPOSITORY_COUNT_DATA_KEY, MARKETPLACE_CUSTOM_REPOSITORY_COUNT_DATA_KEY,
      SORT_BY_DATA_KEY, VENDORS_LIST_FILTER_DATA_KEY, TAGS_LIST_FILTER_DATA_KEY, IS_ORDERED_BY_ML, ML_EXPERIMENT_GROUP,
      ML_EXPERIMENT_VERSION, IS_USER_INTERNAL
    )
  }

  fun getSearchStateFeatures(query: SearchQueryParser.Marketplace): List<EventPair<*>> = buildList {
    val localRanker = MarketplaceLocalRanker.getInstanceIfEnabled()

    addAll(listOf(
      IS_SUGGESTED_DATA_KEY.with(query.suggested),
      IS_STAFF_PICKS_DATA_KEY.with(query.staffPicks),
      CUSTOM_REPOSITORY_COUNT_DATA_KEY.with(query.repositories.size),
      MARKETPLACE_CUSTOM_REPOSITORY_COUNT_DATA_KEY.with(query.repositories.count { it.contains(MarketplaceUrls.getPluginManagerHost()) }),
      IS_ORDERED_BY_ML.with(localRanker != null)
    ))

    localRanker?.run {
      add(ML_EXPERIMENT_GROUP.with(experimentGroup))
      add(ML_EXPERIMENT_VERSION.with(experimentVersion))
    }

    add(IS_USER_INTERNAL.with(ApplicationManager.getApplication().isInternal))

    query.sortBy?.let { add(SORT_BY_DATA_KEY.with(it)) }
    query.vendors?.toList()?.let { add(VENDORS_LIST_FILTER_DATA_KEY.with(it)) }
    query.tags?.toList()?.let { add(TAGS_LIST_FILTER_DATA_KEY.with(it)) }
  }
}