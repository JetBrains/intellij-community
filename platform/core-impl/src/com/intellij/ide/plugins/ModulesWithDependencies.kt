// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet", "ReplaceNegatedIsEmptyWithIsNotEmpty")

package com.intellij.ide.plugins

import java.util.IdentityHashMap

internal class ModulesWithDependencies(
  @JvmField val modules: List<PluginModuleDescriptor>,
  @JvmField val directDependencies: Map<PluginModuleDescriptor, List<PluginModuleDescriptor>>,
) {
  internal fun sorted(topologicalComparator: Comparator<PluginModuleDescriptor>): ModulesWithDependencies {
    return ModulesWithDependencies(
      modules = modules.sortedWith(topologicalComparator),
      directDependencies = copySorted(directDependencies, topologicalComparator),
    )
  }
}

internal fun toCoreAwareComparator(comparator: Comparator<PluginModuleDescriptor>): Comparator<PluginModuleDescriptor> {
  // there is circular reference between core and implementation-detail plugin, as not all such plugins extracted from core,
  // so, ensure that core plugin is always first (otherwise not possible to register actions - a parent group not defined)
  // don't use sortWith here - avoid loading kotlin stdlib
  return Comparator { o1, o2 ->
    val o1isCore = o1 !is ContentModuleDescriptor && o1.pluginId == PluginManagerCore.CORE_ID
    val o2isCore = o2 !is ContentModuleDescriptor && o2.pluginId == PluginManagerCore.CORE_ID
    when {
      o1isCore == o2isCore -> comparator.compare(o1, o2)
      o1isCore -> -1
      else -> 1
    }
  }
}

private fun collectDirectDependenciesInOldFormat(
  rootDescriptor: IdeaPluginDescriptorImpl,
  pluginSet: UnambiguousPluginSet,
  dependenciesCollector: MutableSet<PluginModuleDescriptor>,
  additionalEdges: MutableSet<PluginModuleDescriptor>,
  initContext: PluginInitializationContext,
) {
  for (dependency in rootDescriptor.dependencies) {
    // check for missing optional dependency
    val dependencyPluginId = dependency.pluginId
    val dep = pluginSet.resolvePluginId(dependencyPluginId)
    if (dep == null) {
      dependency.subDescriptor?.isMarkedForLoading = false // target is unresolved
      continue
    }
    if (dep.pluginId != PluginManagerCore.CORE_ID || dep is ContentModuleDescriptor) {
      // ultimate plugin it is combined plugin, where some included XML can define dependency on ultimate explicitly and for now not clear,
      // can be such requirements removed or not
      if (rootDescriptor === dep) {
        if (rootDescriptor.pluginId != PluginManagerCore.CORE_ID) {
          PluginManagerCore.logger.error("Plugin $rootDescriptor depends on self (${dependency})")
        }
      }
      else {
        // e.g. `.env` plugin in an old format and doesn't explicitly specify dependency on new extracted modules
        if (dep is PluginMainDescriptor) {
          dependenciesCollector.addAll(dep.contentModules)
        }

        dependenciesCollector.add(dep)
      }
    }
    if (dep is ContentModuleDescriptor && dep.moduleLoadingRule.required) {
      val dependencyPluginDescriptor = pluginSet.resolvePluginId(dep.pluginId)
      if (dependencyPluginDescriptor != null && dependencyPluginDescriptor !== rootDescriptor) {
        // Add an edge to the main module of the plugin. This is needed to ensure that this plugin is processed after it's decided whether to enable the referenced plugin or not.
        additionalEdges.add(dependencyPluginDescriptor)
      }
    }

    dependency.subDescriptor?.let { subDescriptor ->
      for (implicitDep in initContext.provideCompatibilityDependencies(subDescriptor, pluginSet)) {
        pluginSet.resolveReference(implicitDep)?.let(dependenciesCollector::add)
      }
      collectDirectDependenciesInOldFormat(subDescriptor, pluginSet, dependenciesCollector, additionalEdges, initContext)
    }
  }

  for (pluginId in rootDescriptor.incompatiblePlugins) {
    pluginSet.resolvePluginId(pluginId)?.let {
      dependenciesCollector.add(it)
    }
  }
}

private fun copySorted(
  map: Map<PluginModuleDescriptor, Collection<PluginModuleDescriptor>>,
  comparator: Comparator<PluginModuleDescriptor>,
): Map<PluginModuleDescriptor, List<PluginModuleDescriptor>> {
  val result = IdentityHashMap<PluginModuleDescriptor, List<PluginModuleDescriptor>>(map.size)
  for (element in map.entries) {
    result.put(element.key, element.value.sortedWith(comparator))
  }
  return result
}
