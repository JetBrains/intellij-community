// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.marketplace.statistics.features

import com.intellij.ide.plugins.newui.SearchQueryParser
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair

internal object PluginManagerLocalSearchFeatureProvider {
  private val IS_ENABLED_DATA_KEY = EventFields.Boolean("isEnabled")
  private val IS_DISABLED_DATA_KEY = EventFields.Boolean("isDisabled")
  private val IS_BUNDLED_DATA_KEY = EventFields.Boolean("isBundled")
  private val IS_DOWNLOADED_DATA_KEY = EventFields.Boolean("isDownloaded")
  private val IS_INVALID_DATA_KEY = EventFields.Boolean("isInvalid")
  private val IS_UPDATE_NEEDED_DATA_KEY = EventFields.Boolean("isUpdateNeeded")
  private val WITH_ATTRIBUTES_DATA_KEY = EventFields.Boolean("withAttributes")
  private val TAG_FILTERS_COUNT_DATA_KEY = EventFields.Int("tagFiltersCount")
  private val VENDOR_FILTERS_COUNT_DATA_KEY = EventFields.Int("vendorFiltersCount")

  fun getFeaturesDefinition(): Array<EventField<*>> {
    return arrayOf(
      IS_ENABLED_DATA_KEY, IS_DISABLED_DATA_KEY, IS_BUNDLED_DATA_KEY, IS_DOWNLOADED_DATA_KEY, IS_INVALID_DATA_KEY,
      IS_UPDATE_NEEDED_DATA_KEY, WITH_ATTRIBUTES_DATA_KEY, TAG_FILTERS_COUNT_DATA_KEY, VENDOR_FILTERS_COUNT_DATA_KEY
    )
  }

  fun getSearchStateFeatures(query: SearchQueryParser.Installed) = arrayListOf<EventPair<*>>(
    IS_ENABLED_DATA_KEY.with(query.enabled),
    IS_DISABLED_DATA_KEY.with(query.disabled),
    IS_BUNDLED_DATA_KEY.with(query.bundled),
    IS_DOWNLOADED_DATA_KEY.with(query.downloaded),
    IS_INVALID_DATA_KEY.with(query.invalid),
    IS_UPDATE_NEEDED_DATA_KEY.with(query.needUpdate),
    WITH_ATTRIBUTES_DATA_KEY.with(query.attributes),
    TAG_FILTERS_COUNT_DATA_KEY.with(query.tags?.size ?: 0),
    VENDOR_FILTERS_COUNT_DATA_KEY.with(query.vendors?.size ?: 0)
  )
}