// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.util.graph.Graph
import java.util.*

/**
 * A graph which determines the order in which modules from the platform and the plugins are processed.
 * The graph has a node for each module, and there is an edge from a module to other modules that depend on it.
 */
internal class ModuleGraph internal constructor(
  modulesWithDependencies: ModulesWithDependencies, 
  additionalEdges: IdentityHashMap<PluginModuleDescriptor, List<PluginModuleDescriptor>>
) : Graph<PluginModuleDescriptor> {
  private val nodes = modulesWithDependencies.modules
  private val directDependencies = IdentityHashMap<PluginModuleDescriptor, List<PluginModuleDescriptor>>(modulesWithDependencies.modules.size)
  private val directDependents = IdentityHashMap<PluginModuleDescriptor, ArrayList<PluginModuleDescriptor>>(modulesWithDependencies.modules.size)

  init {
    for (module in modulesWithDependencies.modules) {
      val dependencies = modulesWithDependencies.directDependencies[module]
      val additional = additionalEdges[module]
      val merged = when {
        dependencies != null && additional != null -> additional + dependencies
        dependencies != null -> dependencies
        additional != null -> additional
        else -> null
      }
      if (merged != null) {
        directDependencies[module] = merged
      }
    }
    val edges = HashSet<Map.Entry<IdeaPluginDescriptorImpl, IdeaPluginDescriptorImpl>>()
    for (module in modulesWithDependencies.modules) {
      for (inNode in directDependencies.getOrDefault(module, Collections.emptyList())) {
        if (edges.add(AbstractMap.SimpleImmutableEntry(inNode, module))) {
          // not a duplicate edge
          directDependents.computeIfAbsent(inNode) { ArrayList() }.add(module)
        }
      }
    }
  }
  
  override fun getNodes(): Collection<PluginModuleDescriptor> = nodes

  private fun getDependencies(descriptor: PluginModuleDescriptor): Collection<PluginModuleDescriptor> = directDependencies.getOrDefault(descriptor, Collections.emptyList())

  override fun getIn(descriptor: PluginModuleDescriptor): Iterator<PluginModuleDescriptor> = getDependencies(descriptor).iterator()

  override fun getOut(descriptor: PluginModuleDescriptor): Iterator<PluginModuleDescriptor> = directDependents.getOrDefault(descriptor, Collections.emptyList()).iterator()
}
