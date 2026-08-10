// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")
package com.intellij.ide.plugins

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.util.text.VersionComparatorUtil
import org.jetbrains.annotations.ApiStatus
import java.util.Collections

@ApiStatus.Internal
class PluginSubsystemInput(
  val initContext: PluginInitializationContext,
  val discoveryResult: PluginsDiscoveryResult,
)

@ApiStatus.Internal
class PluginSet(
  val input: PluginSubsystemInput,
  /**
   * Contains plugins that were filtered out early and are not part of the [candidate subset][ResolvedPluginSet.candidateSet].
   * For example, it contains plugins of old versions that were superseded by newer versions ([PluginVersionIsSuperseded]), but may contain
   * other exclusions too.
   */
  val excludedFromCandidateSubset: Map<PluginMainDescriptor, DescriptorExclusionReason>,
  val resolvedPluginSet: ResolvedPluginSet,
) {
  /**
   * Historically, this property only contained one version of each plugin id that is available, while there can be multiple.
   * This contract is preserved. True `allPlugins` can be obtained through [input].
   * TODO deprecate and provide alternative API
   */
  @JvmField val allPlugins: Set<PluginMainDescriptor>

  @JvmField val enabledPlugins: List<PluginMainDescriptor> = resolvedPluginSet.candidateSet.plugins.filter { resolvedPluginSet.isResolved(it) }

  private val enabledModules: List<PluginModuleDescriptor>

  init {
    val mostRecentExcludedPlugins = excludedFromCandidateSubset.keys.asSequence()
      .filter { resolvedPluginSet.candidateSet.resolvePluginId(it.pluginId)?.pluginId != it.pluginId }
      .groupBy { it.pluginId }
      .mapValues {
        if (it.value.size == 1) it.value.first()
        else it.value.maxWith { o1, o2 -> VersionComparatorUtil.compare(o1.version, o2.version) } // take the latest version among excluded disregarding compatibility
      }
    allPlugins = (resolvedPluginSet.candidateSet.plugins + mostRecentExcludedPlugins.values).toSet()

    enabledModules = resolvedPluginSet.sortedResolvedDescriptors.filterIsInstance<PluginModuleDescriptor>()
  }

  fun getEnabledModules(): List<PluginModuleDescriptor> = enabledModules

  internal fun getSortedDependencies(moduleDescriptor: IdeaPluginDescriptorImpl): List<PluginModuleDescriptor> {
    if (moduleDescriptor is DependsSubDescriptor) {
      if (resolvedPluginSet.isExcluded(moduleDescriptor)) {
        return Collections.emptyList()
      }
      val main = moduleDescriptor.getMainDescriptor()
      return resolvedPluginSet.getDirectResolvedDependencies(moduleDescriptor).asSequence()
        .filterIsInstance<PluginModuleDescriptor>()
        .filter { it !== main }
        .toList()
    }
    val dependencies = resolvedPluginSet.getDirectResolvedDependencies(moduleDescriptor)
    if (dependencies.any { it !is PluginModuleDescriptor }) { // expected to always be false, see method's doc
      logger<PluginSet>().error("Module ${moduleDescriptor} contains non-module dependencies: $dependencies")
      return dependencies.filterIsInstance<PluginModuleDescriptor>()
    }
    @Suppress("UNCHECKED_CAST")
    return dependencies as List<PluginModuleDescriptor>
  }

  fun isPluginInstalled(id: PluginId): Boolean = findInstalledPlugin(id) != null

  fun findInstalledPlugin(id: PluginId): PluginMainDescriptor? = allPlugins.find { it.pluginId == id }

  fun isPluginEnabled(id: PluginId): Boolean {
    return findEnabledPlugin(id) != null
  }

  fun findEnabledPlugin(id: PluginId): PluginModuleDescriptor? {
    val module = resolvedPluginSet.candidateSet.resolvePluginId(id)
    if (module != null && resolvedPluginSet.isResolved(module)) {
      return module
    }
    return null
  }

  fun findEnabledModule(moduleId: PluginModuleId): ContentModuleDescriptor? {
    return resolvedPluginSet.candidateSet.resolveContentModuleId(moduleId)
      ?.takeIf { resolvedPluginSet.isResolved(it) }
  }

  fun isModuleEnabled(id: PluginModuleId): Boolean = findEnabledModule(id) != null

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
    return resolvedPluginSet.runtimeModuleGroupGraph.sortedGroups.asSequence()
      .flatMap { it.sortedDescriptors }.filterIsInstance<PluginModuleDescriptor>()
  }

  fun sequenceResolvedSortedDescriptorsForRegistration(): Sequence<IdeaPluginDescriptorImpl> {
    return resolvedPluginSet.sortedResolvedDescriptors.asSequence()
  }

  override fun toString(): String {
    return buildString {
      val resolvedPluginsCount = resolvedPluginSet.sortedResolvedDescriptors.filterIsInstance<PluginMainDescriptor>().count()
      val resolvedContentModulesCount = resolvedPluginSet.sortedResolvedDescriptors.filterIsInstance<ContentModuleDescriptor>().count()
      val excludedModulesCount = resolvedPluginSet.candidateSet.plugins.flatMap { it.sequenceAllDescriptors() }.count { resolvedPluginSet.isExcluded(it) }
      append("PluginSet(resolvedPlugins=${resolvedPluginsCount}, resolvedContentModules=${resolvedContentModulesCount}, excludedModules=${excludedModulesCount})")
    }
  }
}