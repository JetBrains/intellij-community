// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.marketplace.statistics.features

import com.intellij.ide.plugins.enums.SortBy
import com.intellij.ide.plugins.marketplace.statistics.validators.MarketplaceTagsListValidator
import com.intellij.ide.plugins.marketplace.statistics.validators.MarketplaceVendorsListValidator
import com.intellij.ide.plugins.marketplace.utils.MarketplaceUrls
import com.intellij.ide.plugins.newui.SearchQueryParser
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair

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
    "tagsListFilter", MarketplaceTagsListValidator::class.java
  )

  fun getFeaturesDefinition(): Array<EventField<*>> {
    return arrayOf(
      IS_SUGGESTED_DATA_KEY, IS_STAFF_PICKS_DATA_KEY, CUSTOM_REPOSITORY_COUNT_DATA_KEY, MARKETPLACE_CUSTOM_REPOSITORY_COUNT_DATA_KEY,
      SORT_BY_DATA_KEY, VENDORS_LIST_FILTER_DATA_KEY, TAGS_LIST_FILTER_DATA_KEY
    )
  }

  fun getSearchStateFeatures(query: SearchQueryParser.Marketplace): List<EventPair<*>> = buildList {
    addAll(listOf(
      IS_SUGGESTED_DATA_KEY.with(query.suggested),
      IS_STAFF_PICKS_DATA_KEY.with(query.staffPicks),
      CUSTOM_REPOSITORY_COUNT_DATA_KEY.with(query.repositories.size),
      MARKETPLACE_CUSTOM_REPOSITORY_COUNT_DATA_KEY.with(query.repositories.count { it.contains(MarketplaceUrls.pluginManagerHost) })
    ))

    query.sortBy?.let { add(SORT_BY_DATA_KEY.with(it)) }
    query.vendors?.toList()?.let { add(VENDORS_LIST_FILTER_DATA_KEY.with(it)) }
    query.tags?.toList()?.let { add(TAGS_LIST_FILTER_DATA_KEY.with(it)) }
  }
}