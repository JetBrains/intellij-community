// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")
package com.intellij.ide.plugins

import com.intellij.openapi.extensions.PluginId
import com.intellij.util.containers.Java11Shim
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.*

// if otherwise not specified, `module` in terms of v2 plugin model
@ApiStatus.Internal
class PluginSet internal constructor(
  private val sortedModulesWithDependencies: ModulesWithDependencies,
  @JvmField val allPlugins: Set<PluginMainDescriptor>,
  @JvmField val enabledPlugins: List<PluginMainDescriptor>,
  private val enabledModuleMap: Map<String, PluginModuleDescriptor>,
  private val enabledPluginAndV1ModuleMap: Map<PluginId, PluginModuleDescriptor>,
  private val enabledModules: List<PluginModuleDescriptor>,
) {
  /**
   * You must not use this method before [ClassLoaderConfigurator.configure].
   */
  fun getEnabledModules(): List<PluginModuleDescriptor> = enabledModules

  internal fun getSortedDependencies(moduleDescriptor: PluginModuleDescriptor): List<PluginModuleDescriptor> {
    return sortedModulesWithDependencies.directDependencies.getOrDefault(moduleDescriptor, Collections.emptyList())
  }

  @TestOnly
  fun getUnsortedEnabledModules(): Collection<PluginModuleDescriptor> = Java11Shim.INSTANCE.copyOf(enabledModuleMap.values)

  fun isPluginInstalled(id: PluginId): Boolean = findInstalledPlugin(id) != null

  fun findInstalledPlugin(id: PluginId): PluginMainDescriptor? = allPlugins.find { it.pluginId == id }

  fun isPluginEnabled(id: PluginId): Boolean = enabledPluginAndV1ModuleMap.containsKey(id)

  fun findEnabledPlugin(id: PluginId): PluginModuleDescriptor? = enabledPluginAndV1ModuleMap.get(id)

  fun findEnabledModule(id: String): PluginModuleDescriptor? = enabledModuleMap.get(id)

  fun isModuleEnabled(id: String): Boolean = enabledModuleMap.containsKey(id)

  fun withPlugin(plugin: PluginMainDescriptor): PluginSetBuilder {
    // in tests or on plugin installation it is not present in a plugin list, may exist on plugin update, though
    // linear search is ok here - not a hot method
    val oldPlugin = enabledPlugins.find { it == plugin } // todo may exist on update
    PluginManagerCore.logger.assertTrue(oldPlugin == null || !oldPlugin.isMarkedForLoading, "$oldPlugin is still loaded")
    PluginManagerCore.logger.assertTrue(plugin.isMarkedForLoading, "$plugin is not marked for loading")

    val unsortedPlugins = LinkedHashSet(allPlugins)
    unsortedPlugins.removeIf { it == plugin }
    unsortedPlugins.add(plugin)

    return PluginSetBuilder(unsortedPlugins)
  }

  fun withoutPlugin(plugin: PluginMainDescriptor, disable: Boolean = true): PluginSetBuilder {
    return PluginSetBuilder(if (disable) allPlugins else allPlugins - plugin)
  }

  /**
   * Returns a map from plugin ID and plugin aliases to the corresponding plugin or module descriptors from all plugins, not only enabled.
   */
  fun buildPluginIdMap(): Map<PluginId, IdeaPluginDescriptorImpl> {
    val pluginIdResolutionMap = HashMap<PluginId, MutableList<IdeaPluginDescriptorImpl>>()
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
  fun buildContentModuleIdMap(): Map<String, ContentModuleDescriptor> {
    val result = HashMap<String, ContentModuleDescriptor>()
    val enabledPluginIds = enabledPlugins.mapTo(HashSet()) { it.pluginId }
    for (plugin in allPlugins) {
      if (plugin.pluginId !in enabledPluginIds) {
        plugin.contentModules.associateByTo(result, ContentModuleDescriptor::moduleName)
      }
    }
    for (plugin in enabledPlugins) {
      plugin.contentModules.associateByTo(result, ContentModuleDescriptor::moduleName)
    }
    return result
  }
}