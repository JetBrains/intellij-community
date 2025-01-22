// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.util.graph.Graph
import org.jetbrains.annotations.ApiStatus
import java.util.*

/**
 * A graph which determines the order in which modules from the platform and the plugins are processed.
 * The graph has a node for each module, and there is an edge from a module to other modules that depend on it.
 */
internal class ModuleGraph internal constructor(private val modulesWithDependencies: ModulesWithDependencies) : Graph<IdeaPluginDescriptorImpl> {
  private val directDependents = IdentityHashMap<IdeaPluginDescriptorImpl, ArrayList<IdeaPluginDescriptorImpl>>(modulesWithDependencies.modules.size)

  init {
    val edges = HashSet<Map.Entry<IdeaPluginDescriptorImpl, IdeaPluginDescriptorImpl>>()
    val directDependencies = modulesWithDependencies.directDependencies
    for (module in modulesWithDependencies.modules) {
      for (inNode in directDependencies.getOrDefault(module, Collections.emptyList())) {
        if (edges.add(AbstractMap.SimpleImmutableEntry(inNode, module))) {
          // not a duplicate edge
          directDependents.computeIfAbsent(inNode) { ArrayList() }.add(module)
        }
      }
    }
  }
  
  override fun getNodes(): Collection<IdeaPluginDescriptorImpl> = modulesWithDependencies.modules

  private fun getDependencies(descriptor: IdeaPluginDescriptorImpl): Collection<IdeaPluginDescriptorImpl> = modulesWithDependencies.directDependencies.getOrDefault(descriptor, Collections.emptyList())

  override fun getIn(descriptor: IdeaPluginDescriptorImpl): Iterator<IdeaPluginDescriptorImpl> = getDependencies(descriptor).iterator()

  override fun getOut(descriptor: IdeaPluginDescriptorImpl): Iterator<IdeaPluginDescriptorImpl> = directDependents.getOrDefault(descriptor, Collections.emptyList()).iterator()
}
