// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.openapi.extensions.PluginId
import com.intellij.util.graph.DFSTBuilder
import com.intellij.util.graph.Graph
import com.intellij.util.lang.Java11Shim
import org.jetbrains.annotations.ApiStatus
import java.util.*
import java.util.function.Supplier
import java.util.stream.Stream

@ApiStatus.Internal
class CachingSemiGraph<Node>(private val nodes: Collection<Node>,
                             private val pluginToDirectDependencies: Map<Node, List<Node>>) : Graph<Node> {
  private val outs = IdentityHashMap<Node, MutableList<Node>>()

  init {
    buildOuts()
  }

  private fun buildOuts() {
    val edges = Collections.newSetFromMap<Map.Entry<Node, Node>>(HashMap())
    for (node in nodes) {
      for (inNode in (pluginToDirectDependencies.get(node) ?: continue)) {
        if (edges.add(AbstractMap.SimpleImmutableEntry(inNode, node))) {
          // not a duplicate edge
          outs.computeIfAbsent(inNode) { ArrayList() }.add(node)
        }
      }
    }
  }

  override fun getOut(n: Node): Iterator<Node> = outs.get(n)?.iterator() ?: Collections.emptyIterator()

  override fun getNodes() = nodes

  override fun getIn(node: Node): Iterator<Node> {
    return pluginToDirectDependencies.get(node)?.iterator() ?: Collections.emptyIterator()
  }

  fun getInStream(node: Node): Stream<Node> {
    return pluginToDirectDependencies.get(node)?.stream() ?: Stream.empty()
  }
}

fun getTopologicallySorted(descriptors: Collection<IdeaPluginDescriptorImpl>,
                           pluginSet: PluginSet,
                           withOptional: Boolean): List<IdeaPluginDescriptorImpl> {
  val graph = createPluginIdGraph(descriptors = descriptors, pluginSet = pluginSet, withOptional = withOptional)
  val requiredOnlyGraph = DFSTBuilder(graph)
  val comparator = requiredOnlyGraph.comparator()
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

@ApiStatus.Internal
fun createPluginIdGraph(descriptors: Collection<IdeaPluginDescriptorImpl>,
                        pluginSet: PluginSet,
                        withOptional: Boolean): CachingSemiGraph<PluginId> {
  val hasAllModules = pluginSet.isPluginEnabled(PluginManagerCore.ALL_MODULES_MARKER)
  val javaDep = Supplier {
    pluginSet.findEnabledPlugin(PluginManagerCore.JAVA_MODULE_ID)
  }

  val uniqueCheck = Collections.newSetFromMap<PluginId>(IdentityHashMap())
  val pluginToDirectDependencies = IdentityHashMap<PluginId, List<PluginId>>(descriptors.size)
  val list = ArrayList<PluginId>(32)
  val ids = arrayOfNulls<PluginId>(descriptors.size)
  var index = 0
  for (descriptor in descriptors) {
    ids[index++] = descriptor.id
    collectDirectDependencies(descriptor, pluginSet, withOptional, hasAllModules, javaDep, uniqueCheck, list)
    if (!list.isEmpty()) {
      pluginToDirectDependencies.put(descriptor.id, Java11Shim.INSTANCE.copyOf(list))
      list.clear()
    }
  }
  return CachingSemiGraph(Java11Shim.INSTANCE.listOf<PluginId>(ids), pluginToDirectDependencies)
}

private fun collectDirectDependencies(rootDescriptor: IdeaPluginDescriptorImpl,
                                      pluginSet: PluginSet,
                                      withOptional: Boolean,
                                      hasAllModules: Boolean,
                                      javaDep: Supplier<IdeaPluginDescriptorImpl?>,
                                      uniqueCheck: MutableSet<PluginId>,
                                      result: MutableList<PluginId>) {
  val implicitDep = if (hasAllModules) PluginManagerCore.getImplicitDependency(rootDescriptor, javaDep) else null
  uniqueCheck.clear()
  if (implicitDep != null) {
    if (rootDescriptor === implicitDep) {
      PluginManagerCore.getLogger().error("Plugin $rootDescriptor depends on self")
    }
    else {
      uniqueCheck.add(implicitDep.id)
      result.add(implicitDep.id)
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
    else if (uniqueCheck.add(dep.id)) {
      result.add(dep.id)
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
    if (dep != null && uniqueCheck.add(dep.id)) {
      result.add(dep.id)
    }
  }
}

private fun directDependenciesOfModule(module: IdeaPluginDescriptorImpl,
                                       pluginSet: PluginSet,
                                       uniqueCheck: MutableSet<PluginId>,
                                       result: MutableList<PluginId>) {
  processDirectDependencies(module, pluginSet) {
    if (uniqueCheck.add(it.id)) {
      result.add(it.id)
    }
  }
}

inline fun processDirectDependencies(module: IdeaPluginDescriptorImpl,
                                     pluginSet: PluginSet,
                                     processor: (IdeaPluginDescriptorImpl) -> Unit) {
  for (item in module.dependencies.modules) {
    val descriptor = pluginSet.findEnabledModule(item.name)
    if (descriptor != null) {
      processor(descriptor)
    }
  }
  for (item in module.dependencies.plugins) {
    val descriptor = pluginSet.findEnabledPlugin(item.id)
    if (descriptor != null) {
      processor(descriptor)
    }
  }
}