// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("ReplaceGetOrSet")
package com.intellij.serviceContainer

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.ExtensionDescriptor
import com.intellij.openapi.extensions.ExtensionPointDescriptor
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PrecomputedExtensionModel(
  @JvmField val extensionPoints: List<List<ExtensionPointDescriptor>>,
  @JvmField val pluginDescriptors: List<IdeaPluginDescriptor>,
  @JvmField val extensionPointTotalCount: Int,

  @JvmField val nameToExtensions: Map<String, MutableList<Pair<IdeaPluginDescriptor, List<ExtensionDescriptor>>>>
)

fun precomputeExtensionModel(): PrecomputedExtensionModel {
  val extensionPointDescriptors = ArrayList<List<ExtensionPointDescriptor>>()
  val pluginDescriptors = ArrayList<IdeaPluginDescriptor>()
  var extensionPointTotalCount = 0
  val nameToExtensions = HashMap<String, MutableList<Pair<IdeaPluginDescriptor, List<ExtensionDescriptor>>>>()

  // step 1 - collect container level extension points
  val modules = PluginManagerCore.getPluginSet().getEnabledModules()
  executeRegisterTask(modules) { pluginDescriptor ->
    pluginDescriptor.moduleContainerDescriptor.extensionPoints?.let {
      extensionPointDescriptors.add(it)
      pluginDescriptors.add(pluginDescriptor)
      extensionPointTotalCount += it.size

      for (descriptor in it) {
        nameToExtensions.put(descriptor.getQualifiedName(pluginDescriptor), mutableListOf())
      }
    }
  }

  // step 2 - collect container level extensions
  executeRegisterTask(modules) { pluginDescriptor ->
    val unsortedMap = pluginDescriptor.epNameToExtensions ?: return@executeRegisterTask
    for ((name, list) in unsortedMap.entries) {
      nameToExtensions.get(name)?.add(pluginDescriptor to list)
    }
  }

  return PrecomputedExtensionModel(
    extensionPoints = extensionPointDescriptors,
    pluginDescriptors = pluginDescriptors,
    extensionPointTotalCount = extensionPointTotalCount,

    nameToExtensions = nameToExtensions,
  )
}

private inline fun executeRegisterTask(modules: Sequence<IdeaPluginDescriptorImpl>, crossinline task: (IdeaPluginDescriptorImpl) -> Unit) {
  for (module in modules) {
    task(module)
    executeRegisterTaskForOldContent(mainPluginDescriptor = module, task = task)
  }
}

inline fun executeRegisterTaskForOldContent(mainPluginDescriptor: IdeaPluginDescriptorImpl,
                                            crossinline task: (IdeaPluginDescriptorImpl) -> Unit) {
  for (dep in mainPluginDescriptor.pluginDependencies) {
    val subDescriptor = dep.subDescriptor
    if (subDescriptor?.pluginClassLoader == null) {
      continue
    }

    task(subDescriptor)

    for (subDep in subDescriptor.pluginDependencies) {
      val d = subDep.subDescriptor
      if (d?.pluginClassLoader != null) {
        task(d)
        assert(d.pluginDependencies.isEmpty() || d.pluginDependencies.all { it.subDescriptor == null })
      }
    }
  }
}