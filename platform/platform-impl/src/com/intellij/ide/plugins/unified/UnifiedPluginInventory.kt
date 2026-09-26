// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.unified

import com.intellij.ide.plugins.newui.PluginInventoryLoadResult
import com.intellij.ide.plugins.newui.PluginInventorySnapshot
import com.intellij.ide.plugins.newui.PluginSource
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.openapi.extensions.PluginId

internal data class UnifiedPluginInventory(
  val installedPlugins: List<UnifiedPluginInventoryItem>,
  val bundledPlugins: List<UnifiedPluginInventoryItem>,
  val unavailableSides: Set<PluginSource> = emptySet(),
)

internal data class UnifiedPluginInventoryItem(
  val model: PluginUiModel,
  val runtimeOn: PluginSource?,
  val stagedOn: PluginSource?,
  val bundledOn: PluginSource?,
) {
  val installedOn: PluginSource = checkNotNull(combineOptionalSides(runtimeOn, stagedOn))
}

internal fun normalizePluginInventory(snapshot: PluginInventorySnapshot): UnifiedPluginInventory {
  val localPlugins = normalizeSide(snapshot, PluginSource.LOCAL)
  val remotePlugins = normalizeSide(snapshot, PluginSource.REMOTE)
  val installed = ArrayList<UnifiedPluginInventoryItem>()
  val bundled = ArrayList<UnifiedPluginInventoryItem>()

  for (pluginId in orderedPluginIds(localPlugins, remotePlugins)) {
    val local = localPlugins[pluginId]
    val remote = remotePlugins[pluginId]
    if (local?.isImplementationDetail == true || remote?.isImplementationDetail == true) continue

    val item = UnifiedPluginInventoryItem(
      model = checkNotNull(local?.model ?: remote?.model),
      runtimeOn = presentSides(local?.hasRuntime == true, remote?.hasRuntime == true),
      stagedOn = presentSides(local?.hasStaged == true, remote?.hasStaged == true),
      bundledOn = presentSides(local?.isBundled == true, remote?.isBundled == true),
    )
    item.model.source = item.installedOn
    if (item.bundledOn == null) {
      installed.add(item)
    }
    else {
      bundled.add(item)
    }
  }

  return UnifiedPluginInventory(installedPlugins = installed, bundledPlugins = bundled)
}

internal fun normalizePluginInventory(result: PluginInventoryLoadResult): UnifiedPluginInventory {
  return normalizePluginInventory(result.snapshot).copy(unavailableSides = result.unavailableSides)
}

private data class SidePlugin(
  val model: PluginUiModel,
  val hasRuntime: Boolean,
  val hasStaged: Boolean,
  val isBundled: Boolean,
  val isImplementationDetail: Boolean,
)

private fun normalizeSide(snapshot: PluginInventorySnapshot, side: PluginSource): Map<PluginId, SidePlugin> {
  val runtimeById = snapshot.runtimePlugins.filter { it.side == side }.groupBy { it.model.pluginId }
  val stagedById = snapshot.stagedPlugins.filter { it.side == side }.groupBy { it.model.pluginId }
  val result = LinkedHashMap<PluginId, SidePlugin>()

  for (pluginId in orderedPluginIds(runtimeById, stagedById)) {
    val runtimeEntries = runtimeById[pluginId].orEmpty()
    val stagedEntries = stagedById[pluginId].orEmpty()
    val allEntries = runtimeEntries + stagedEntries
    result[pluginId] = SidePlugin(
      model = checkNotNull(stagedEntries.lastOrNull() ?: runtimeEntries.lastOrNull()).model,
      hasRuntime = runtimeEntries.isNotEmpty(),
      hasStaged = stagedEntries.isNotEmpty(),
      isBundled = allEntries.any { entry -> entry.model.isBundled || entry.model.isBundledUpdate },
      isImplementationDetail = allEntries.any { it.model.isImplementationDetail },
    )
  }
  return result
}

private fun orderedPluginIds(
  first: Map<PluginId, *>,
  second: Map<PluginId, *>,
): Set<PluginId> = LinkedHashSet<PluginId>().apply {
  addAll(first.keys)
  addAll(second.keys)
}

private fun presentSides(local: Boolean, remote: Boolean): PluginSource? {
  return when {
    local && remote -> PluginSource.BOTH
    local -> PluginSource.LOCAL
    remote -> PluginSource.REMOTE
    else -> null
  }
}

private fun combineOptionalSides(first: PluginSource?, second: PluginSource?): PluginSource? {
  if (first == null) return second
  if (second == null || first == second) return first
  return PluginSource.BOTH
}
