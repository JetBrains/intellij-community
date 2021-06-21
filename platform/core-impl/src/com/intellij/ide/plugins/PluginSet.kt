// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.openapi.extensions.PluginId
import com.intellij.util.lang.Java11Shim
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

// if otherwise not specified, `module` in terms of v2 plugin model
@ApiStatus.Internal
class PluginSet(
  @JvmField val allPlugins: List<IdeaPluginDescriptorImpl>,
  enabledPlugins: List<IdeaPluginDescriptorImpl>,
  checkModulesDependencies: Boolean = true,
) {
  @JvmField val enabledPlugins: List<IdeaPluginDescriptorImpl> = Java11Shim.INSTANCE.copyOf(enabledPlugins)
  private val enabledPluginAndV1ModuleMap: Map<PluginId, IdeaPluginDescriptorImpl>
  // module map in a new v2 format
  private val moduleMap: Map<String, PluginContentDescriptor.ModuleItem>

  init {
    val enabledPluginAndV1ModuleMap = HashMap<PluginId, IdeaPluginDescriptorImpl>(enabledPlugins.size)
    val moduleMap = HashMap<String, PluginContentDescriptor.ModuleItem>()
    for (descriptor in enabledPlugins) {
      addWithV1Modules(enabledPluginAndV1ModuleMap, descriptor)

      m@ for (item in descriptor.content.modules) {
        if (checkModulesDependencies) {
          for (ref in item.requireDescriptor().dependencies.modules) {
            if (!moduleMap.containsKey(ref.name)) {
              continue@m
            }
          }
          for (ref in item.requireDescriptor().dependencies.plugins) {
            if (!enabledPluginAndV1ModuleMap.containsKey(ref.id)) {
              continue@m
            }
          }
        }

        moduleMap.putIfAbsent(item.name, item)?.let {
          throw RuntimeException("Duplicated module name (first=$it, second=$item)")
        }
      }

      if (descriptor.packagePrefix != null) {
        val pluginAsModuleItem = PluginContentDescriptor.ModuleItem(name = descriptor.id.idString, configFile = null)
        pluginAsModuleItem.descriptor = descriptor
        moduleMap.put(pluginAsModuleItem.name, pluginAsModuleItem)
      }
    }

    val java11Shim = Java11Shim.INSTANCE
    this.enabledPluginAndV1ModuleMap = java11Shim.copyOf(enabledPluginAndV1ModuleMap)
    this.moduleMap = java11Shim.copyOf(moduleMap)
  }

  @TestOnly
  fun getUnsortedEnabledModules(): Collection<PluginContentDescriptor.ModuleItem> = ArrayList(moduleMap.values)

  fun isPluginEnabled(id: PluginId) = enabledPluginAndV1ModuleMap.containsKey(id)

  fun findEnabledPlugin(id: PluginId): IdeaPluginDescriptorImpl? = enabledPluginAndV1ModuleMap.get(id)

  fun findEnabledModule(id: String): IdeaPluginDescriptorImpl? = moduleMap.get(id)?.requireDescriptor()

  fun isModuleEnabled(id: String) = moduleMap.containsKey(id)

  fun concat(descriptor: IdeaPluginDescriptorImpl): PluginSet {
    return PluginSet(allPlugins = allPlugins.plus(descriptor),
                     enabledPlugins = Java11Shim.INSTANCE.copyOf(enabledPlugins.plus(descriptor)))
  }

  private fun addWithV1Modules(result: MutableMap<PluginId, IdeaPluginDescriptorImpl>, descriptor: IdeaPluginDescriptorImpl) {
    result.put(descriptor.id, descriptor)
    for (module in descriptor.modules) {
      result.put(module, descriptor)
    }
  }
}