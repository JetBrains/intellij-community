// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")
package com.intellij.ide.plugins

import com.intellij.openapi.extensions.PluginId
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

// if otherwise not specified, `module` in terms of v2 plugin model
@ApiStatus.Internal
class PluginSet internal constructor(
  @JvmField val moduleGraph: ModuleGraph,
  @JvmField val allPlugins: List<IdeaPluginDescriptorImpl>,
  @JvmField val enabledPlugins: List<IdeaPluginDescriptorImpl>,
  private val enabledModuleMap: Map<String, IdeaPluginDescriptorImpl>,
  private val enabledPluginAndV1ModuleMap: Map<PluginId, IdeaPluginDescriptorImpl>,
  private val enabledModules: List<IdeaPluginDescriptorImpl>,
) {
  fun getRawListOfEnabledModules() = enabledModules

  /**
   * You must not use this method before [ClassLoaderConfigurator.configure].
   */
  fun getEnabledModules(): Sequence<IdeaPluginDescriptorImpl> = enabledModules.asSequence()

  @TestOnly
  fun getUnsortedEnabledModules(): Collection<IdeaPluginDescriptorImpl> = ArrayList(enabledModuleMap.values)

  fun isPluginInstalled(id: PluginId) = findInstalledPlugin(id) != null

  fun findInstalledPlugin(id: PluginId): IdeaPluginDescriptorImpl? = allPlugins.find { it.pluginId == id }

  fun isPluginEnabled(id: PluginId) = enabledPluginAndV1ModuleMap.containsKey(id)

  fun findEnabledPlugin(id: PluginId): IdeaPluginDescriptorImpl? = enabledPluginAndV1ModuleMap.get(id)

  fun findEnabledModule(id: String): IdeaPluginDescriptorImpl? = enabledModuleMap.get(id)

  fun isModuleEnabled(id: String) = enabledModuleMap.containsKey(id)

  fun enablePlugin(toEnable: IdeaPluginDescriptorImpl): PluginSet {
    // in tests or on install plugin is not in all plugins
    // linear search is ok here - not a hot method
    PluginManagerCore.getLogger().assertTrue(!enabledPlugins.contains(toEnable) && toEnable.isEnabled)
    return PluginSetBuilder(if (toEnable in allPlugins) allPlugins else allPlugins + toEnable).computeEnabledModuleMap().createPluginSet()
  }

  fun updateEnabledPlugins() = PluginSetBuilder(allPlugins).computeEnabledModuleMap().createPluginSet()

  fun removePluginAndUpdateEnabledPlugins(descriptor: IdeaPluginDescriptorImpl): PluginSet {
    // not just remove from enabledPlugins - maybe another plugins in list also disabled as result of plugin unloading
    return PluginSetBuilder(unsortedPlugins = allPlugins - descriptor).computeEnabledModuleMap().createPluginSet()
  }
}