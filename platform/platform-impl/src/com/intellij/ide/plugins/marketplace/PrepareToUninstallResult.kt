// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.marketplace

import com.intellij.openapi.extensions.PluginId
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@Serializable
@ApiStatus.Internal
data class PrepareToUninstallResult(
  val dependants: Map<PluginId, List<String>>,
  val bundledPlugins: List<PluginId>,
) {
  fun isPluginBundled(pluginId: PluginId): Boolean = pluginId in bundledPlugins
}