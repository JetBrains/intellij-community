// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.unified

import com.intellij.ide.plugins.marketplace.statistics.UnifiedPluginSearchStatistics
import com.intellij.ide.plugins.marketplace.statistics.enums.UnifiedPluginSearchFilterKind
import com.intellij.ide.plugins.marketplace.statistics.enums.UnifiedPluginSearchQueryShape
import com.intellij.ide.plugins.marketplace.statistics.enums.UnifiedPluginSearchSection
import com.intellij.ide.plugins.marketplace.statistics.enums.UnifiedPluginSearchSourceKind

internal fun unifiedPluginSearchStatistics(state: UnifiedPluginsPageSourceState): UnifiedPluginSearchStatistics {
  require(state.query.normalizedQuery.isNotEmpty()) { "Unified search statistics require a non-empty query" }
  val query = UnifiedPluginsQuery.parse(state.query.normalizedQuery)
  val hasText = query.parts.any { it is UnifiedPluginQueryPart.Other }
  val hasControls = query.parts.any { it !is UnifiedPluginQueryPart.Other }
  val queryShape = when {
    hasText && hasControls -> UnifiedPluginSearchQueryShape.TEXT_AND_CONTROLS
    hasControls -> UnifiedPluginSearchQueryShape.CONTROLS_ONLY
    else -> UnifiedPluginSearchQueryShape.TEXT_ONLY
  }
  val filterKinds = buildSet {
    query.parts.asSequence()
      .filterIsInstance<UnifiedPluginQueryPart.Attribute>()
      .mapNotNullTo(this) { part -> part.attribute.searchFilterKind }
    if (query.effectiveInstalledFilter != null) add(UnifiedPluginSearchFilterKind.INSTALLED_STATE)
    if (query.parts.any { part ->
        part is UnifiedPluginQueryPart.Command && part.command in INSTALLATION_TYPE_COMMANDS
      }) {
      add(UnifiedPluginSearchFilterKind.INSTALLATION_TYPE)
    }
  }
  val route = state.query.sourceRoute()
  val sourceKinds = buildSet {
    if (route.local.eligible) add(UnifiedPluginSearchSourceKind.LOCAL)
    if (route.internal.eligible) add(UnifiedPluginSearchSourceKind.INTERNAL)
    when (route.marketplaceMode) {
      UnifiedPluginMarketplaceSourceMode.Suggested -> add(UnifiedPluginSearchSourceKind.SUGGESTED)
      UnifiedPluginMarketplaceSourceMode.Search -> add(UnifiedPluginSearchSourceKind.MARKETPLACE)
      UnifiedPluginMarketplaceSourceMode.Inactive -> Unit
    }
    if (route.repositories.eligible) add(UnifiedPluginSearchSourceKind.CUSTOM_REPOSITORY)
  }
  val resultCounts = UnifiedPluginSearchSection.entries.associateWithTo(LinkedHashMap()) { 0 }
  state.sections.forEach { section ->
    val statisticsSection = section.id.statisticsSection ?: return@forEach
    resultCounts[statisticsSection] = resultCounts.getValue(statisticsSection) + section.items.size
  }
  return UnifiedPluginSearchStatistics(queryShape, filterKinds, sourceKinds, query.effectiveSort, resultCounts)
}

private val UnifiedPluginQueryAttribute.searchFilterKind: UnifiedPluginSearchFilterKind?
  get() = when (this) {
    UnifiedPluginQueryAttribute.Vendor -> UnifiedPluginSearchFilterKind.VENDOR
    UnifiedPluginQueryAttribute.Category -> UnifiedPluginSearchFilterKind.CATEGORY
    UnifiedPluginQueryAttribute.Tag -> UnifiedPluginSearchFilterKind.TAG
    UnifiedPluginQueryAttribute.Repository -> UnifiedPluginSearchFilterKind.REPOSITORY
    UnifiedPluginQueryAttribute.Sort -> null
  }

private val PluginSectionId.statisticsSection: UnifiedPluginSearchSection?
  get() = when (this) {
    PluginSectionId.Installing -> UnifiedPluginSearchSection.INSTALLING
    PluginSectionId.Installed -> UnifiedPluginSearchSection.INSTALLED
    PluginSectionId.Bundled -> UnifiedPluginSearchSection.BUNDLED
    PluginSectionId.Internal -> UnifiedPluginSearchSection.INTERNAL
    PluginSectionId.Suggested -> UnifiedPluginSearchSection.SUGGESTED
    PluginSectionId.Marketplace -> UnifiedPluginSearchSection.MARKETPLACE
    PluginSectionId.CustomRepositoryCatalog -> null
    is PluginSectionId.CustomRepository -> UnifiedPluginSearchSection.CUSTOM_REPOSITORY
  }

private val INSTALLATION_TYPE_COMMANDS = setOf(
  UnifiedPluginQueryCommand.Bundled,
  UnifiedPluginQueryCommand.UserInstalled,
  UnifiedPluginQueryCommand.Downloaded,
)
