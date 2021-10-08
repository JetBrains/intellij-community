// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.util.graph.DFSTBuilder
import com.intellij.util.lang.Java11Shim
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.*

// if otherwise not specified, `module` in terms of v2 plugin model
@ApiStatus.Internal
class PluginSet internal constructor(
  @JvmField val allPlugins: List<IdeaPluginDescriptorImpl>,
  @JvmField val enabledPlugins: List<IdeaPluginDescriptorImpl>,
  private val enabledModuleMap: Map<String, PluginContentDescriptor.ModuleItem>,
  private val enabledPluginAndV1ModuleMap: Map<PluginId, IdeaPluginDescriptorImpl>,
) {
  companion object {
    // special case - raw plugin set where everything is enabled and resolved
    fun createRawPluginSet(plugins: List<IdeaPluginDescriptorImpl>): PluginSet {
      val java11Shim = Java11Shim.INSTANCE
      val enabledModuleV2Ids = HashMap<String, PluginContentDescriptor.ModuleItem>()
      val enabledPluginAndModuleV1Map = HashMap<PluginId, IdeaPluginDescriptorImpl>(plugins.size)
      for (descriptor in plugins) {
        addWithV1Modules(enabledPluginAndModuleV1Map, descriptor)
        for (item in descriptor.content.modules) {
          enabledModuleV2Ids[item.name] = item
        }
      }
      return PluginSet(
        allPlugins = plugins,
        enabledPlugins = plugins,
        enabledModuleMap = java11Shim.copyOf(enabledModuleV2Ids),
        enabledPluginAndV1ModuleMap = java11Shim.copyOf(enabledPluginAndModuleV1Map),
      )
    }

    fun createPluginSet(allPlugins: List<IdeaPluginDescriptorImpl>, enabledPlugins: List<IdeaPluginDescriptorImpl>): PluginSet {
      val enabledModuleV2Ids = HashMap<String, PluginContentDescriptor.ModuleItem>()
      val enabledPluginAndModuleV1Map = HashMap<PluginId, IdeaPluginDescriptorImpl>(enabledPlugins.size)

      val log = PluginManagerCore.getLogger()
      val isDebugLogEnabled = log.isDebugEnabled || !System.getProperty("plugin.classloader.debug", "").isEmpty()
      for (descriptor in enabledPlugins) {
        addWithV1Modules(enabledPluginAndModuleV1Map, descriptor)
        checkModules(descriptor, enabledPluginAndModuleV1Map, enabledModuleV2Ids, isDebugLogEnabled, log)
      }

      val java11Shim = Java11Shim.INSTANCE
      return PluginSet(
        allPlugins = java11Shim.copyOf(allPlugins),
        enabledPlugins = java11Shim.copyOf(enabledPlugins),
        enabledModuleMap = java11Shim.copyOf(enabledModuleV2Ids),
        enabledPluginAndV1ModuleMap = java11Shim.copyOf(enabledPluginAndModuleV1Map),
      )
    }

    fun addWithV1Modules(result: MutableMap<PluginId, IdeaPluginDescriptorImpl>, descriptor: IdeaPluginDescriptorImpl) {
      result[descriptor.id] = descriptor
      for (module in descriptor.modules) {
        result[module] = descriptor
      }
    }

    fun getOnlyEnabledPlugins(sortedAll: Collection<IdeaPluginDescriptorImpl>): List<IdeaPluginDescriptorImpl> {
      return sortedAll.filterTo(ArrayList(sortedAll.size)) { it.isEnabled }
    }

    fun checkModules(descriptor: IdeaPluginDescriptorImpl,
                     enabledPluginIds: Map<PluginId, IdeaPluginDescriptorImpl>,
                     enabledModuleV2Ids: MutableMap<String, PluginContentDescriptor.ModuleItem>,
                     isDebugLogEnabled: Boolean,
                     log: Logger) {
      m@ for (item in descriptor.content.modules) {
        for (ref in item.requireDescriptor().dependencies.modules) {
          if (!enabledModuleV2Ids.containsKey(ref.name)) {
            if (isDebugLogEnabled) {
              log.info("Module ${item.name} is not enabled because dependency ${ref.name} is not available")
            }
            continue@m
          }
        }
        for (ref in item.requireDescriptor().dependencies.plugins) {
          if (!enabledPluginIds.containsKey(ref.id)) {
            if (isDebugLogEnabled) {
              log.info("Module ${item.name} is not enabled because dependency ${ref.id} is not available")
            }
            continue@m
          }
        }
        enabledModuleV2Ids[item.name] = item
      }
    }
  }

  @TestOnly
  fun getUnsortedEnabledModules(): Collection<PluginContentDescriptor.ModuleItem> = ArrayList(enabledModuleMap.values)

  fun isPluginEnabled(id: PluginId) = enabledPluginAndV1ModuleMap.containsKey(id)

  fun findEnabledPlugin(id: PluginId): IdeaPluginDescriptorImpl? = enabledPluginAndV1ModuleMap[id]

  fun findEnabledModule(id: String): IdeaPluginDescriptorImpl? = enabledModuleMap[id]?.requireDescriptor()

  fun isModuleEnabled(id: String) = enabledModuleMap.containsKey(id)

  fun enablePlugin(descriptor: IdeaPluginDescriptorImpl): PluginSet {
    // in tests or on install plugin is not in all plugins
    // linear search is ok here - not a hot method
    PluginManagerCore.getLogger().assertTrue(!enabledPlugins.contains(descriptor))

    return createPluginSet(
      allPlugins = if (descriptor in allPlugins) allPlugins else sortTopologically(allPlugins + descriptor),
      enabledPlugins = sortTopologically(enabledPlugins + descriptor),
    )
  }

  fun sortTopologically(
    descriptors: List<IdeaPluginDescriptorImpl>,
    withOptional: Boolean = true,
  ): List<IdeaPluginDescriptorImpl> {
    val graph = CachingSemiGraph.createPluginIdGraph(descriptors, pluginSet = this, withOptional)

    val comparator = DFSTBuilder(graph).comparator()
    // there is circular reference between core and implementation-detail plugin, as not all such plugins extracted from core,
    // so, ensure that core plugin is always first (otherwise not possible to register actions - parent group not defined)
    // don't use sortWith here - avoid loading kotlin stdlib
    val sortedRequired = descriptors.toTypedArray()
    Arrays.sort(sortedRequired, Comparator { o1, o2 ->
      when (PluginManagerCore.CORE_ID) {
        o1.id -> -1
        o2.id -> 1
        else -> comparator.compare(o1.id, o2.id)
      }
    })
    @Suppress("ReplaceJavaStaticMethodWithKotlinAnalog", "UNCHECKED_CAST")
    return Java11Shim.INSTANCE.listOf(sortedRequired)
  }

  fun updateEnabledPlugins(): PluginSet = updateEnabledPlugins(allPlugins)

  fun removePluginAndUpdateEnabledPlugins(descriptor: IdeaPluginDescriptorImpl): PluginSet {
    // not just remove from enabledPlugins - maybe another plugins in list also disabled as result of plugin unloading
    return updateEnabledPlugins(allPlugins - descriptor)
  }

  private fun updateEnabledPlugins(allPlugins: List<IdeaPluginDescriptorImpl>): PluginSet {
    return createPluginSet(
      allPlugins,
      enabledPlugins = getOnlyEnabledPlugins(allPlugins),
    )
  }
}