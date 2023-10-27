// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")
package com.intellij.serviceContainer

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.openapi.extensions.ExtensionDescriptor
import com.intellij.openapi.extensions.ExtensionPointDescriptor
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PrecomputedExtensionModel(
  @JvmField val extensionPoints: PersistentList<PersistentList<ExtensionPointDescriptor>>,
  @JvmField val pluginDescriptors: PersistentList<IdeaPluginDescriptor>,

  @JvmField val nameToExtensions: PersistentMap<String, PersistentList<Pair<IdeaPluginDescriptor, PersistentList<ExtensionDescriptor>>>>,

  @JvmField val isInitial: Boolean,
)

@ApiStatus.Internal
fun precomputeModuleLevelExtensionModel(modules: List<IdeaPluginDescriptorImpl>, isInitial: Boolean): PrecomputedExtensionModel {
  var extensionPointDescriptors = persistentListOf<PersistentList<ExtensionPointDescriptor>>()
  var pluginDescriptors = persistentListOf<IdeaPluginDescriptor>()
  var extensionPointTotalCount = 0
  var nameToExtensions = persistentHashMapOf<String, PersistentList<Pair<IdeaPluginDescriptor, PersistentList<ExtensionDescriptor>>>>()

  // step 1 - collect container level extension points
  executeRegisterTask(modules) { pluginDescriptor ->
    val list = pluginDescriptor.moduleContainerDescriptor.extensionPoints
    if (list.isEmpty()) {
      return@executeRegisterTask
    }

    extensionPointDescriptors = extensionPointDescriptors.add(list)
    pluginDescriptors = pluginDescriptors.add(pluginDescriptor)
    extensionPointTotalCount += list.size

    for (descriptor in list) {
      nameToExtensions = nameToExtensions.put(descriptor.getQualifiedName(pluginDescriptor), persistentListOf())
    }
  }

  // step 2 - collect container level extensions
  executeRegisterTask(modules) { pluginDescriptor ->
    val map = pluginDescriptor.epNameToExtensions
    for ((name, list) in map.entries) {
      nameToExtensions.get(name)?.let {
        nameToExtensions.put(name, it.add(pluginDescriptor to list))
      }
    }
  }

  return PrecomputedExtensionModel(
    extensionPoints = extensionPointDescriptors,
    pluginDescriptors = pluginDescriptors,

    nameToExtensions = nameToExtensions,
    isInitial = isInitial,
  )
}

private inline fun executeRegisterTask(modules: List<IdeaPluginDescriptorImpl>, crossinline task: (IdeaPluginDescriptorImpl) -> Unit) {
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