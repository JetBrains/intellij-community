// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

internal class ModulesWithDependencies(
  @JvmField val modules: List<PluginModuleDescriptor>,
  @JvmField val directDependencies: Map<PluginModuleDescriptor, List<PluginModuleDescriptor>>,
)

internal fun toCoreAwareComparator(comparator: Comparator<PluginModuleDescriptor>): Comparator<PluginModuleDescriptor> {
  // there is circular reference between core and implementation-detail plugin, as not all such plugins extracted from core,
  // so, ensure that core plugin is always first (otherwise not possible to register actions - a parent group not defined)
  // don't use sortWith here - avoid loading kotlin stdlib
  return Comparator { o1, o2 ->
    val o1isCore = o1 !is ContentModuleDescriptor && o1.pluginId == PluginManagerCore.CORE_ID
    val o2isCore = o2 !is ContentModuleDescriptor && o2.pluginId == PluginManagerCore.CORE_ID
    when {
      o1isCore == o2isCore -> comparator.compare(o1, o2)
      o1isCore -> -1
      else -> 1
    }
  }
}