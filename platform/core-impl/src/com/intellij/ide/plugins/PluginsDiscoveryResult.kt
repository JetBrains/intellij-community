// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface PluginsDiscoveryResult {
  val pluginLists: List<DiscoveredPluginsList>
  val descriptorLoadingErrors: List<PluginDescriptorLoadingError>

  companion object {
    fun build(
      discoveredPluginLists: List<DiscoveredPluginsList>,
      descriptorLoadingErrors: List<PluginDescriptorLoadingError> = emptyList(),
    ): PluginsDiscoveryResult {
      val errors = descriptorLoadingErrors
      return object : PluginsDiscoveryResult {
        override val pluginLists: List<DiscoveredPluginsList> = discoveredPluginLists
        override val descriptorLoadingErrors: List<PluginDescriptorLoadingError> = errors
      }
    }
  }
}
