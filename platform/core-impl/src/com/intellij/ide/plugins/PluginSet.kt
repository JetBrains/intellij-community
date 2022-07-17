// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")
package com.intellij.ide.plugins

import com.intellij.openapi.extensions.PluginId
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

// if otherwise not specified, `module` in terms of v2 plugin model
@ApiStatus.Internal
class PluginSet internal constructor(
  @JvmField val moduleGraph: ModuleGraph,
  @JvmField val allPlugins: Set<IdeaPluginDescriptorImpl>,
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

  fun withModule(module: IdeaPluginDescriptorImpl): PluginSetBuilder {
    // in tests or on install plugin is not in all plugins
    // linear search is ok here - not a hot method
    PluginManagerCore.getLogger().assertTrue(!enabledPlugins.contains(module) && module.isEnabled)
    return PluginSetBuilder(allPlugins + module)
  }

  fun withoutModule(
    module: IdeaPluginDescriptorImpl,
    disable: Boolean = true,
  ) = PluginSetBuilder(if (disable) allPlugins else allPlugins - module)
}