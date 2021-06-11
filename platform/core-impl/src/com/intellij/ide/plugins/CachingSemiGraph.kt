// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.util.graph.DFSTBuilder
import com.intellij.util.graph.GraphGenerator
import com.intellij.util.graph.InboundSemiGraph
import com.intellij.util.lang.Java11Shim
import java.util.*
import java.util.function.Supplier
import java.util.stream.Stream

internal class CachingSemiGraph<Node>(private val nodes: Collection<Node>,
                                      private val pluginToDirectDependencies: Map<Node, List<Node>>) : InboundSemiGraph<Node> {
  override fun getNodes() = nodes

  override fun getIn(n: Node): Iterator<Node> {
    return (pluginToDirectDependencies.get(n) ?: return Collections.emptyIterator()).iterator()
  }

  fun getInStream(n: Node): Stream<Node> {
    return (pluginToDirectDependencies.get(n) ?: return Stream.empty()).stream()
  }
}

fun getTopologicallySorted(descriptors: Collection<IdeaPluginDescriptorImpl>,
                           pluginSet: PluginSet,
                           withOptional: Boolean): List<IdeaPluginDescriptorImpl> {
  val graph = createPluginIdGraph(descriptors = descriptors,
                                  pluginSet = pluginSet,
                                  withOptional = withOptional)
  val requiredOnlyGraph = DFSTBuilder(GraphGenerator.generate(graph))
  val sortedRequired = ArrayList(graph.nodes)
  val comparator = requiredOnlyGraph.comparator()
  // there is circular reference between core and implementation-detail plugin, as not all such plugins extracted from core,
  // so, ensure that core plugin is always first (otherwise not possible to register actions - parent group not defined)
  // don't use sortWith here - avoid loading kotlin stdlib
  Collections.sort(sortedRequired, Comparator { o1, o2 ->
    when (PluginManagerCore.CORE_ID) {
      o1.pluginId -> -1
      o2.pluginId -> 1
      else -> comparator.compare(o1, o2)
    }
  })
  return sortedRequired
}

internal fun createPluginIdGraph(descriptors: Collection<IdeaPluginDescriptorImpl>,
                                 pluginSet: PluginSet,
                                 withOptional: Boolean): CachingSemiGraph<IdeaPluginDescriptorImpl> {
  val hasAllModules = pluginSet.isPluginEnabled(PluginManagerCore.ALL_MODULES_MARKER)
  val javaDep = Supplier {
    pluginSet.findEnabledPlugin(PluginManagerCore.JAVA_MODULE_ID)
  }

  val uniqueCheck = HashSet<IdeaPluginDescriptorImpl>()
  val pluginToDirectDependencies = HashMap<IdeaPluginDescriptorImpl, List<IdeaPluginDescriptorImpl>>(descriptors.size)
  val list = ArrayList<IdeaPluginDescriptorImpl>(32)
  for (descriptor in descriptors) {
    collectDirectDependencies(descriptor, pluginSet, withOptional, hasAllModules, javaDep, uniqueCheck, list)
    if (!list.isEmpty()) {
      pluginToDirectDependencies.put(descriptor, Java11Shim.INSTANCE.copyOf(list))
      list.clear()
    }
  }
  return CachingSemiGraph(descriptors, pluginToDirectDependencies)
}

private fun collectDirectDependencies(rootDescriptor: IdeaPluginDescriptorImpl,
                                      pluginSet: PluginSet,
                                      withOptional: Boolean,
                                      hasAllModules: Boolean,
                                      javaDep: Supplier<IdeaPluginDescriptorImpl?>,
                                      uniqueCheck: MutableSet<IdeaPluginDescriptorImpl>,
                                      result: MutableList<IdeaPluginDescriptorImpl>) {
  val implicitDep = if (hasAllModules) PluginManagerCore.getImplicitDependency(rootDescriptor, javaDep) else null
  uniqueCheck.clear()
  if (implicitDep != null) {
    if (rootDescriptor === implicitDep) {
      PluginManagerCore.getLogger().error("Plugin $rootDescriptor depends on self")
    }
    else {
      uniqueCheck.add(implicitDep)
      result.add(implicitDep)
    }
  }
  for (dependency in rootDescriptor.pluginDependencies) {
    if (!withOptional && dependency.isOptional) {
      continue
    }

    // check for missing optional dependency
    val dep = pluginSet.findEnabledPlugin(dependency.pluginId) ?: continue

    // if 'dep' refers to a module we need to check the real plugin containing this module only if it's still enabled,
    // otherwise the graph will be inconsistent

    // ultimate plugin it is combined plugin, where some included XML can define dependency on ultimate explicitly and for now not clear,
    // can be such requirements removed or not
    if (rootDescriptor === dep) {
      if (PluginManagerCore.CORE_ID != rootDescriptor.pluginId) {
        PluginManagerCore.getLogger().error("Plugin $rootDescriptor depends on self")
      }
    }
    else if (uniqueCheck.add(dep)) {
      result.add(dep)
    }
  }

  directDependenciesOfModule(rootDescriptor, pluginSet, uniqueCheck, result)

  // graph for plugins, not for modules - so, dependency of content must be taken into account
  if (rootDescriptor.id != PluginManagerCore.CORE_ID) {
    for (module in rootDescriptor.content.modules) {
      directDependenciesOfModule(module.requireDescriptor(), pluginSet, uniqueCheck, result)
    }
  }
  for (moduleId in rootDescriptor.incompatibilities) {
    val dep = pluginSet.findEnabledPlugin(moduleId)
    if (dep != null && uniqueCheck.add(dep)) {
      result.add(dep)
    }
  }
}

private fun directDependenciesOfModule(module: IdeaPluginDescriptorImpl,
                                       pluginSet: PluginSet,
                                       uniqueCheck: MutableSet<IdeaPluginDescriptorImpl>,
                                       result: MutableList<IdeaPluginDescriptorImpl>) {
  processDirectDependencies(module, pluginSet) {
    if (uniqueCheck.add(it)) {
      result.add(it)
    }
  }
}

inline fun processDirectDependencies(module: IdeaPluginDescriptorImpl,
                                     pluginSet: PluginSet,
                                     processor: (IdeaPluginDescriptorImpl) -> Unit) {
  for (item in module.dependencies.modules) {
    val dep = pluginSet.findEnabledModule(item.name)
    if (dep != null) {
      processor(dep)
    }
  }
  for (item in module.dependencies.plugins) {
    val descriptor = pluginSet.findEnabledPlugin(item.id)
    if (descriptor != null) {
      processor(descriptor)
    }
  }
}