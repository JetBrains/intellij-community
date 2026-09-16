// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class PluginInventorySnapshot(
  val runtimePlugins: List<PluginInventoryEntry>,
  val stagedPlugins: List<PluginInventoryEntry>,
)

@ApiStatus.Internal
data class PluginInventoryLoadResult(
  val snapshot: PluginInventorySnapshot,
  val unavailableSides: Set<PluginSource> = emptySet(),
) {
  init {
    require(PluginSource.BOTH !in unavailableSides) { "Unavailable inventory sides must be physical targets" }
  }
}

@ApiStatus.Internal
data class PluginInventoryEntry(
  val model: PluginUiModel,
  val side: PluginSource,
) {
  init {
    require(side != PluginSource.BOTH) { "Inventory entries must retain one originating side" }
  }
}
