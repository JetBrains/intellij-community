// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.openapi.extensions.PluginId
import com.intellij.util.lang.Java11Shim
import org.jetbrains.annotations.ApiStatus

// if otherwise not specified, `module` in terms of v2 plugin model
@ApiStatus.Internal
class PluginSet(
  @JvmField val allPlugins: List<IdeaPluginDescriptorImpl>,
  loadedPlugins: List<IdeaPluginDescriptorImpl>,
) {
  @JvmField val loadedPlugins: List<IdeaPluginDescriptorImpl> = Java11Shim.INSTANCE.copyOf(loadedPlugins)

  private val enabledPluginAndV1ModuleMap: Map<PluginId, IdeaPluginDescriptorImpl>
  //private val allPluginAndV1ModuleMap: Map<PluginId, IdeaPluginDescriptorImpl>

  // module map in a new v2 format
  private val moduleMap: Map<String, PluginContentDescriptor.ModuleItem>

  init {
    val enabledPluginAndV1ModuleMap = HashMap<PluginId, IdeaPluginDescriptorImpl>(loadedPlugins.size)
    //val allPluginAndV1ModuleMap = HashMap<PluginId, IdeaPluginDescriptorImpl>(allPlugins.size)
    val moduleMap = HashMap<String, PluginContentDescriptor.ModuleItem>()
    for (descriptor in loadedPlugins) {
      addWithV1Modules(enabledPluginAndV1ModuleMap, descriptor)

      for (module in descriptor.content.modules) {
        moduleMap.putIfAbsent(module.name, module)?.let {
          throw RuntimeException("Duplicated module name (first=$it, second=$module)")
        }
      }

      if (descriptor.packagePrefix != null) {
        val pluginAsModuleItem = PluginContentDescriptor.ModuleItem(name = descriptor.id.idString, configFile = null)
        pluginAsModuleItem.descriptor = descriptor
        moduleMap.put(pluginAsModuleItem.name, pluginAsModuleItem)
      }
    }

    //for (descriptor in allPlugins) {
    //  addWithV1Modules(allPluginAndV1ModuleMap, descriptor)
    //}

    val java11Shim = Java11Shim.INSTANCE
    this.enabledPluginAndV1ModuleMap = java11Shim.copyOf(enabledPluginAndV1ModuleMap)
    //this.allPluginAndV1ModuleMap = java11Shim.copyOf(allPluginAndV1ModuleMap)
    this.moduleMap = java11Shim.copyOf(moduleMap)
  }

  fun isPluginEnabled(id: PluginId): Boolean = enabledPluginAndV1ModuleMap.containsKey(id)

  fun findEnabledPlugin(id: PluginId): IdeaPluginDescriptorImpl? = enabledPluginAndV1ModuleMap.get(id)

  //fun findPlugin(id: PluginId): IdeaPluginDescriptorImpl? = allPluginAndV1ModuleMap.get(id)

  // module in term of v2 model
  fun findEnabledModule(id: String): PluginContentDescriptor.ModuleItem? = moduleMap.get(id)

  fun concat(descriptor: IdeaPluginDescriptorImpl): PluginSet {
    return PluginSet(allPlugins = allPlugins.plus(descriptor),
                     loadedPlugins = Java11Shim.INSTANCE.copyOf(loadedPlugins.plus(descriptor)))
  }

  private fun addWithV1Modules(result: MutableMap<PluginId, IdeaPluginDescriptorImpl>, descriptor: IdeaPluginDescriptorImpl) {
    result.put(descriptor.id, descriptor)
    for (module in descriptor.modules) {
      result.put(module, descriptor)
    }
  }
}