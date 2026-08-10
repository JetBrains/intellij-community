// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.PluginManagerCore.CORE_ID
import com.intellij.openapi.extensions.PluginId
import com.intellij.util.text.VersionComparatorUtil
import org.jetbrains.annotations.ApiStatus

/**
 * Selects one candidate version per plugin ID and resolves ID conflicts. The resulting descriptors are not necessarily loadable;
 * [PluginInitializationContext.resolveConstraints] subsequently excludes disabled, incompatible, and otherwise invalid candidates.
 * 
 * The selection process depends on the configuration:
 * 
 * **Regular mode** (default):
 * 1. Selects the most recent compatible version per plugin ID, or the most recent version if none are compatible
 * 2. Resolves ID conflicts (plugins/modules declaring the same ID or alias), using loading hints when one descriptor is preferable
 * 
 * **Explicit subset mode** ([PluginInitializationContext.explicitPluginSubsetToLoad] is set):
 * 1. Selects plugin versions as in regular mode
 * 2. Keeps only explicitly listed and essential plugins and their transitive dependencies
 * 3. Resolves ID conflicts using hints supplied by the initialization context
 * 
 * **Disabled completely mode** ([PluginInitializationContext.disablePluginLoadingCompletely] is true):
 * - Loads only the CORE plugin, all others are excluded
 * - Resolves ID conflicts (though typically only CORE remains)
 *
 * @param excludedPluginsCollector Map populated with the exclusion reason for each excluded [PluginMainDescriptor], or `null` if exclusion
 * reasons do not need to be collected
 * @return [UnambiguousPluginSet] containing the candidate subset with all ID conflicts resolved
 */
@ApiStatus.Internal
fun PluginInitializationContext.selectCandidateSubset(
  discoveryResult: PluginsDiscoveryResult,
  excludedPluginsCollector: MutableMap<PluginMainDescriptor, DescriptorExclusionReason>?,
): UnambiguousPluginSet {
  val discoveredPlugins = discoveryResult.pluginLists
  if (discoveredPlugins.isEmpty()) {
    return UnambiguousPluginSet.tryBuild(emptyList())!!
  }
  val candidatePlugins = if (explicitPluginSubsetToLoad != null) {
    // does not care about disabled plugins and incompatible-with for essential plugins
    selectFromExplicitSubset(discoveredPlugins, excludedPluginsCollector)
  }
  else if (disablePluginLoadingCompletely) {
    selectOnlyCorePlugin(discoveredPlugins, excludedPluginsCollector)
  }
  else {
    selectMostRecentCompatibleOrJustMostRecentPerPluginId(discoveredPlugins, excludedPluginsCollector)
  }
  return resolveIdConflicts(candidatePlugins, excludedPluginsCollector)
}

/**
 * if there are more than one version of a plugin, we select the newest compatible plugin (or just the newest if there are no compatible ones)
 */
private fun PluginInitializationContext.selectMostRecentCompatibleOrJustMostRecentPerPluginId(
  discoveredPlugins: List<DiscoveredPluginsList>,
  excludedPluginsCollector: MutableMap<PluginMainDescriptor, DescriptorExclusionReason>?,
): List<PluginMainDescriptor> {
  val selectedPluginsByPluginId = LinkedHashMap<PluginId, PluginMainDescriptor>()
  for (pluginList in discoveredPlugins) {
    for (plugin in pluginList.plugins) {
      val pluginId = plugin.pluginId
      val existingPlugin = selectedPluginsByPluginId[pluginId]
      if (existingPlugin == null) {
        selectedPluginsByPluginId[pluginId] = plugin
        continue
      }

      val existingIncompatibility = validatePluginIsCompatible(existingPlugin)
      val pluginIncompatibility = validatePluginIsCompatible(plugin)
      if (existingIncompatibility != null && pluginIncompatibility == null) {
        excludedPluginsCollector?.put(existingPlugin, existingIncompatibility)
        selectedPluginsByPluginId[pluginId] = plugin
        continue
      }
      if (existingIncompatibility == null && pluginIncompatibility != null) {
        excludedPluginsCollector?.put(plugin, pluginIncompatibility)
        continue
      }

      // plugins added via property shouldn't be overridden to avoid plugin root detection issues when running external plugin tests
      if (VersionComparatorUtil.compare(plugin.version, existingPlugin.version) > 0 ||
          pluginList.source is PluginsSourceContext.SystemPropertyProvided) {
        excludedPluginsCollector?.put(existingPlugin, PluginVersionIsSuperseded(existingPlugin, plugin))
        selectedPluginsByPluginId[pluginId] = plugin
      }
      else {
        excludedPluginsCollector?.put(plugin, PluginVersionIsSuperseded(plugin, existingPlugin))
      }
    }
  }

  return discoveredPlugins.flatMap { pluginList ->
    pluginList.plugins.filter { selectedPluginsByPluginId[it.pluginId] === it }
  }
}

private fun PluginInitializationContext.selectFromExplicitSubset(
  discoveredPlugins: List<DiscoveredPluginsList>,
  excludedPluginsCollector: MutableMap<PluginMainDescriptor, DescriptorExclusionReason>?,
): List<PluginMainDescriptor> {
  val plugins = selectMostRecentCompatibleOrJustMostRecentPerPluginId(discoveredPlugins, excludedPluginsCollector)
  val explicitSubset = explicitPluginSubsetToLoad ?: emptySet()
  val pluginIdsSubset = essentialPlugins + explicitSubset // TODO consider explicit subset as essential and exclude everything non-essential, move this logic into constraint resolver
  val pluginSubset = plugins.filter { it.pluginId in pluginIdsSubset }
  val ambiguousPluginSet = AmbiguousPluginSet.build(plugins)
  val requiredModules = PluginDependencyAnalysis.getRequiredTransitiveModules(
    this,
    pluginSubset,
    ambiguousPluginSet,
  )
  return plugins.filter { plugin ->
    if (plugin in requiredModules) {
      true
    } else {
      excludedPluginsCollector?.put(plugin, ProductRulesImposedExclusion(plugin, PluginIsNotContainedInTheExplicitlyConfiguredSubsetOfPluginsForLoading))
      false
    }
  }
}


private fun selectOnlyCorePlugin(
  discoveredPlugins: List<DiscoveredPluginsList>,
  excludedPluginsCollector: MutableMap<PluginMainDescriptor, DescriptorExclusionReason>?,
): List<PluginMainDescriptor> {
  return discoveredPlugins.flatMap { pluginsList ->
    pluginsList.plugins.filter { plugin ->
      if (plugin.pluginId == CORE_ID) {
        true
      }
      else {
        excludedPluginsCollector?.put(plugin, ProductRulesImposedExclusion(plugin, PluginLoadingIsDisabledCompletelyExceptCore))
        false
      }
    }
  }
}
