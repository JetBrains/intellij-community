// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.PluginManagerCore.CORE_ID
import com.intellij.openapi.extensions.PluginId
import com.intellij.util.text.VersionComparatorUtil
import org.jetbrains.annotations.ApiStatus

/**
 * Selects plugins to load by applying all business logic for plugin selection and ID conflict resolution.
 * 
 * The selection process depends on the configuration:
 * 
 * **Regular mode** (default):
 * 1. Validates plugin compatibility (build version constraints)
 * 2. Selects the most recent version per plugin ID
 * 3. Filters out disabled plugins (except those required by essential plugins)
 * 4. Filters out plugins incompatible with essential plugins (except those required by essential plugins)
 * 5. Resolves ID conflicts (plugins/modules declaring the same ID or alias)
 * 
 * **Explicit subset mode** ([PluginInitializationContext.explicitPluginSubsetToLoad] is set):
 * 1. Validates plugin compatibility
 * 2. Selects the most recent version per plugin ID
 * 3. Loads only explicitly listed plugins and their transitive dependencies
 * 4. Does NOT filter disabled plugins or incompatible-with declarations
 * 5. Resolves ID conflicts
 * 
 * **Disabled completely mode** ([PluginInitializationContext.disablePluginLoadingCompletely] is true):
 * - Loads only the CORE plugin, all others are excluded
 * - Resolves ID conflicts (though typically only CORE remains)
 *
 * @param onPluginExcluded Callback invoked for each excluded plugin
 * @return [UnambiguousPluginSet] containing plugins selected for loading with all ID conflicts resolved
 */
@ApiStatus.Internal
fun PluginInitializationContext.selectPluginsToLoad(
  discoveredPlugins: List<DiscoveredPluginsList>,
  onPluginExcluded: (PluginMainDescriptor, PluginNonLoadReason) -> Unit,
): UnambiguousPluginSet {
  if (discoveredPlugins.isEmpty()) {
    return UnambiguousPluginSet.tryBuild(emptyList())!!
  }
  val pluginsToLoad = if (explicitPluginSubsetToLoad != null) {
    // does not care about disabled plugins and incompatible-with for essential plugins
    selectFromExplicitSubset(discoveredPlugins, onPluginExcluded)
  }
  else if (disablePluginLoadingCompletely) {
    selectOnlyCorePlugin(discoveredPlugins, onPluginExcluded)
  }
  else {
    val compatible = selectMostRecentCompatible(discoveredPlugins, onPluginExcluded)
    val filtered = applyDisabledAndIncompatibleWithForEssentialPluginsFilters(compatible, onPluginExcluded)
    filtered
  }
  return resolveIdConflicts(pluginsToLoad, onPluginExcluded)
}

private fun PluginInitializationContext.selectMostRecentCompatible(
  discoveredPlugins: List<DiscoveredPluginsList>,
  onPluginExcluded: (PluginMainDescriptor, PluginNonLoadReason) -> Unit,
): List<PluginMainDescriptor> {
  val selectedPluginsByPluginId = LinkedHashMap<PluginId, PluginMainDescriptor>()
  for (pluginList in discoveredPlugins) {
    for (plugin in pluginList.plugins) {
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

  return discoveredPlugins.flatMap { pluginList ->
    pluginList.plugins.filter { selectedPluginsByPluginId[it.pluginId] === it }
  }
}

private fun PluginInitializationContext.applyDisabledAndIncompatibleWithForEssentialPluginsFilters(
  compatiblePlugins: List<PluginMainDescriptor>,
  onPluginExcluded: (PluginMainDescriptor, PluginNonLoadReason) -> Unit
): List<PluginMainDescriptor> {
  val essentialIds = essentialPlugins + setOf(CORE_ID)
  val essentialPlugins = compatiblePlugins.filter { it.pluginId in essentialIds }
  val ambiguousPluginSet = AmbiguousPluginSet.build(compatiblePlugins)
  val allEssentialModules = PluginDependencyAnalysis.getRequiredTransitiveModules(
    this,
    essentialPlugins,
    ambiguousPluginSet,
  )
  // we should exclude incompatibilities for essential modules early, because otherwise it might result in a dependency cycle later
  // note: includes possible incompatible-with for content modules IJPL-200858
  val incompatibleWithEssentialModules = hashMapOf<PluginModuleDescriptor, PluginModuleDescriptor>()
  for (essentialModule in allEssentialModules) {
    for (incompatibleId in essentialModule.incompatiblePlugins) {
      for (incompatibleModule in ambiguousPluginSet.resolvePluginId(incompatibleId)) {
        incompatibleWithEssentialModules.putIfAbsent(incompatibleModule, essentialModule)
      }
    }
  }
  return compatiblePlugins.filter { plugin ->
    when {
      isPluginDisabled(plugin.pluginId) && plugin !in allEssentialModules -> {
        onPluginExcluded(plugin, PluginIsMarkedDisabled(plugin))
        false
      }
      plugin in incompatibleWithEssentialModules.keys && plugin !in allEssentialModules -> {
        onPluginExcluded(plugin, PluginIsIncompatibleWithAnotherPlugin(plugin, incompatibleWithEssentialModules[plugin]!!, true))
        false
      }
      else -> {
        true
      }
    }
  }
}

private fun PluginInitializationContext.selectFromExplicitSubset(
  discoveredPlugins: List<DiscoveredPluginsList>,
  onPluginExcluded: (PluginMainDescriptor, PluginNonLoadReason) -> Unit,
): List<PluginMainDescriptor> {
  val compatiblePlugins = selectMostRecentCompatible(discoveredPlugins, onPluginExcluded)
  val pluginIdsSubset = essentialPlugins + explicitPluginSubsetToLoad!! + setOf(CORE_ID)
  val pluginSubset = compatiblePlugins.filter { it.pluginId in pluginIdsSubset }
  val ambiguousPluginSet = AmbiguousPluginSet.build(compatiblePlugins)
  val requiredModules = PluginDependencyAnalysis.getRequiredTransitiveModules(
    this,
    pluginSubset,
    ambiguousPluginSet,
  )
  return compatiblePlugins.filter { plugin ->
    if (plugin in requiredModules) {
      true
    } else {
      onPluginExcluded(plugin, PluginIsNotRequiredForLoadingTheExplicitlyConfiguredSubsetOfPlugins(plugin))
      false
    }
  }
}


private fun selectOnlyCorePlugin(
  discoveredPlugins: List<DiscoveredPluginsList>,
  onPluginExcluded: (PluginMainDescriptor, PluginNonLoadReason) -> Unit,
): List<PluginMainDescriptor> {
  return discoveredPlugins.flatMap { pluginsList ->
    pluginsList.plugins.filter { plugin ->
      if (plugin.pluginId == CORE_ID) {
        true
      }
      else {
        onPluginExcluded(plugin, PluginLoadingIsDisabledCompletely(plugin))
        false
      }
    }
  }
}