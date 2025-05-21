// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")
package com.intellij.ide.plugins

import com.intellij.openapi.extensions.PluginId
import com.intellij.util.Java11Shim
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
    PluginManagerCore.logger.assertTrue((oldPlugin == null || !oldPlugin.isEnabled) && plugin.isEnabled)

    val unsortedPlugins = LinkedHashSet(allPlugins)
    unsortedPlugins.removeIf { it == plugin }
    unsortedPlugins.add(plugin)

    return PluginSetBuilder(unsortedPlugins)
  }

  fun withoutPlugin(plugin: PluginMainDescriptor, disable: Boolean = true): PluginSetBuilder {
    return PluginSetBuilder(if (disable) allPlugins else allPlugins - plugin)
  }
}