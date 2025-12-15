// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.extensions.PluginId
import com.intellij.util.text.VersionComparatorUtil
import org.jetbrains.annotations.ApiStatus

/**
 * Selects plugins to load by applying all business logic for plugin selection.
 * This is a single comprehensive phase that:
 * - Filters out disabled plugins
 * - Validates plugin compatibility
 * - Selects the most recent version per plugin ID
 *
 * @param onPluginExcluded Callback invoked for each excluded plugin
 * @return Filtered list containing only plugins selected for loading.
 *         Returns the original list if no exclusions occurred, otherwise returns filtered lists.
 */
@ApiStatus.Internal
fun PluginInitializationContext.selectPluginsToLoad(
  discoveredPlugins: List<DiscoveredPluginsList>,
  onPluginExcluded: (PluginMainDescriptor, PluginNonLoadReason) -> Unit,
): List<DiscoveredPluginsList> {
  if (discoveredPlugins.isEmpty()) {
    return emptyList()
  }

  val selectedPluginsByPluginId = LinkedHashMap<PluginId, PluginMainDescriptor>()
  var hasExclusions = false
  // name shadowing is intended
  val onPluginExcluded: (PluginMainDescriptor, PluginNonLoadReason) -> Unit = { plugin, reason ->
    hasExclusions = true
    onPluginExcluded(plugin, reason)
  }

  for (pluginList in discoveredPlugins) {
    for (plugin in pluginList.plugins) {
      if (isPluginDisabled(plugin.pluginId)) {
        onPluginExcluded(plugin, PluginIsMarkedDisabled(plugin))
        continue
      }
      validatePluginIsCompatible(plugin)?.let { reason ->
        onPluginExcluded(plugin, reason)
        continue
      }

      val pluginId = plugin.pluginId
      val existingPlugin = selectedPluginsByPluginId[pluginId]
      if (existingPlugin == null) {
        selectedPluginsByPluginId[pluginId] = plugin
        continue
      }

      // plugins added via property shouldn't be overridden to avoid plugin root detection issues when running external plugin tests
      if (VersionComparatorUtil.compare(plugin.version, existingPlugin.version) > 0 ||
          pluginList.source is PluginsSourceContext.SystemPropertyProvided) {
        onPluginExcluded(existingPlugin, PluginVersionIsSuperseded(existingPlugin, plugin))
        selectedPluginsByPluginId[pluginId] = plugin
      }
      else {
        onPluginExcluded(plugin, PluginVersionIsSuperseded(plugin, existingPlugin))
      }
    }
  }

  if (!hasExclusions) {
    return discoveredPlugins
  }
  return discoveredPlugins.map { pluginList ->
    val filteredPlugins = pluginList.plugins.filter { selectedPluginsByPluginId[it.pluginId] === it }
    DiscoveredPluginsList(filteredPlugins, pluginList.source)
  }
}