// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.marketplace.statistics

import com.intellij.ide.plugins.MarketplaceTabSearchSortByOptions
import com.intellij.ide.plugins.marketplace.statistics.enums.UnifiedPluginSearchFilterKind
import com.intellij.ide.plugins.marketplace.statistics.enums.UnifiedPluginSearchQueryShape
import com.intellij.ide.plugins.marketplace.statistics.enums.UnifiedPluginSearchSection
import com.intellij.ide.plugins.marketplace.statistics.enums.UnifiedPluginSearchSourceKind

internal data class UnifiedPluginSearchStatistics(
  val queryShape: UnifiedPluginSearchQueryShape,
  val filterKinds: Set<UnifiedPluginSearchFilterKind>,
  val sourceKinds: Set<UnifiedPluginSearchSourceKind>,
  val sort: MarketplaceTabSearchSortByOptions,
  val resultCounts: Map<UnifiedPluginSearchSection, Int>,
)
