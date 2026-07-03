// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")
package com.intellij.ide.plugins

import com.intellij.openapi.extensions.PluginId
import com.intellij.util.containers.Java11Shim
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.Collections

@ApiStatus.Internal
class PluginSubsystemInput(
  val initContext: PluginInitializationContext,
  val discoveryResult: PluginsDiscoveryResult,
)

// if otherwise not specified, `module` in terms of v2 plugin model
@ApiStatus.Internal
class PluginSet internal constructor(
  private val sortedModulesWithDependencies: ModulesWithDependencies,
  @JvmField val allPlugins: Set<PluginMainDescriptor>,
  @JvmField val enabledPlugins: List<PluginMainDescriptor>,
  private val enabledModuleMap: Map<PluginModuleId, ContentModuleDescriptor>,
  private val enabledPluginAndV1ModuleMap: Map<PluginId, PluginModuleDescriptor>,
  private val enabledModules: List<PluginModuleDescriptor>,
  private val topologicalComparator: Comparator<PluginModuleDescriptor>,
  val resolvedPluginSet: ResolvedPluginSet?,
  val input: PluginSubsystemInput,
) {
  /**
   * You must not use this method before [ClassLoaderConfigurator.configure].
   */
  fun getEnabledModules(): List<PluginModuleDescriptor> = enabledModules

  internal fun getSortedDependencies(moduleDescriptor: IdeaPluginDescriptorImpl): List<PluginModuleDescriptor> {
    if (moduleDescriptor is DependsSubDescriptor) {
      if (resolvedPluginSet == null || resolvedPluginSet.isExcluded(moduleDescriptor)) {
        return Collections.emptyList()
      }
      val main = moduleDescriptor.getMainDescriptor()
      return resolvedPluginSet.getDirectResolvedDependencies(moduleDescriptor).asSequence()
        .filterIsInstance<PluginModuleDescriptor>()
        .filter { it !== main }
        .toList()
    }
    return sortedModulesWithDependencies.directDependencies.getOrDefault(moduleDescriptor, Collections.emptyList())
  }

  @TestOnly
  fun getUnsortedEnabledModules(): Collection<ContentModuleDescriptor> = Java11Shim.INSTANCE.copyOf(enabledModuleMap.values)

  fun isPluginInstalled(id: PluginId): Boolean = findInstalledPlugin(id) != null

  fun findInstalledPlugin(id: PluginId): PluginMainDescriptor? = allPlugins.find { it.pluginId == id }

  fun isPluginEnabled(id: PluginId): Boolean = enabledPluginAndV1ModuleMap.containsKey(id)

  fun findEnabledPlugin(id: PluginId): PluginModuleDescriptor? = enabledPluginAndV1ModuleMap.get(id)

  fun findEnabledModule(moduleId: PluginModuleId): ContentModuleDescriptor? = enabledModuleMap.get(moduleId)

  fun isModuleEnabled(id: PluginModuleId): Boolean = enabledModuleMap.containsKey(id)

  /**
   * Returns a map from plugin ID and plugin aliases to the corresponding plugin or module descriptors from all plugins, not only enabled.
   */
  fun buildPluginIdMap(): Map<PluginId, PluginModuleDescriptor> {
    val pluginIdResolutionMap = HashMap<PluginId, MutableList<PluginModuleDescriptor>>()
    for (plugin in allPlugins) {
      pluginIdResolutionMap.computeIfAbsent(plugin.pluginId) { ArrayList() }.add(plugin)
      for (pluginAlias in plugin.pluginAliases) {
        pluginIdResolutionMap.computeIfAbsent(pluginAlias) { ArrayList() }.add(plugin)
      }
      for (contentModule in plugin.contentModules) {
        // plugin aliases in content modules are resolved as plugin id references
        for (pluginAlias in contentModule.pluginAliases) {
          pluginIdResolutionMap.computeIfAbsent(pluginAlias) { ArrayList() }.add(contentModule)
        }
      }
    }
    // FIXME this is a bad way to treat ambiguous plugin ids
    return pluginIdResolutionMap.asSequence().filter { it.value.size == 1 }.associateTo(HashMap()) { it.key to it.value[0] }
  }

  /**
   * Returns a map from content module ID (name) to the corresponding descriptor from all plugins, not only enabled.
   */
  fun buildContentModuleIdMap(): Map<PluginModuleId, ContentModuleDescriptor> {
    val result = HashMap<PluginModuleId, ContentModuleDescriptor>()
    val enabledPluginIds = enabledPlugins.mapTo(HashSet()) { it.pluginId }
    for (plugin in allPlugins) {
      if (plugin.pluginId !in enabledPluginIds) {
        plugin.contentModules.associateByTo(result, ContentModuleDescriptor::moduleId)
      }
    }
    for (plugin in enabledPlugins) {
      plugin.contentModules.associateByTo(result, ContentModuleDescriptor::moduleId)
    }
    return result
  }

  fun getModulesOrderedForClassLoaderConfiguration(): Sequence<PluginModuleDescriptor> {
    return if (resolvedPluginSet != null) {
      resolvedPluginSet.runtimeModuleGroupGraph.sortedGroups.asSequence()
        .flatMap { it.sortedDescriptors }.filterIsInstance<PluginModuleDescriptor>()
    } else {
      enabledModules.asSequence()
    }
  }

  fun sequenceResolvedSortedDescriptorsForRegistration(): Sequence<IdeaPluginDescriptorImpl> {
    return if (resolvedPluginSet != null) {
      resolvedPluginSet.sortedResolvedDescriptors.asSequence()
    } else {
      sequence {
        for (module in enabledModules) {
          yield(module)
          sequenceSubDescriptorsForRegistration(module)
        }
      }
    }
  }

  override fun toString(): String {
    return buildString {
      val resolvedPluginsCount = resolvedPluginSet?.sortedResolvedDescriptors?.filterIsInstance<PluginMainDescriptor>()?.count()
      val resolvedContentModulesCount = resolvedPluginSet?.sortedResolvedDescriptors?.filterIsInstance<ContentModuleDescriptor>()?.count()
      val excludedModulesCount = resolvedPluginSet?.candidateSet?.plugins?.flatMap { it.sequenceAllDescriptors() }?.count { resolvedPluginSet.isExcluded(it) }
      append("PluginSet(resolvedPlugins=${resolvedPluginsCount}, resolvedContentModules=${resolvedContentModulesCount}, excludedModules=${excludedModulesCount})")
    }
  }
}

@ApiStatus.Internal
suspend fun SequenceScope<IdeaPluginDescriptorImpl>.sequenceSubDescriptorsForRegistration(moduleDescriptor: IdeaPluginDescriptorImpl) {
  if (!moduleDescriptor.isMarkedForLoading) {
    return
  }
  for (dep in moduleDescriptor.dependencies) {
    val subDescriptor = dep.subDescriptor
    if (subDescriptor?.isMarkedForLoading != true) {
      continue
    }

    yield(subDescriptor)

    for (subDep in subDescriptor.dependencies) {
      val d = subDep.subDescriptor
      if (d?.isMarkedForLoading == true) {
        yield(d)
        assert(d.dependencies.isEmpty() || d.dependencies.all { it.subDescriptor == null })
      }
    }
  }
}