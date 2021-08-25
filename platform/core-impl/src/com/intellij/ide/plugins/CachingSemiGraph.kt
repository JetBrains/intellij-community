// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")
package com.intellij.ide.plugins

import com.intellij.util.ArrayUtilRt
import com.intellij.util.graph.DFSTBuilder
import com.intellij.util.lang.Java11Shim
import java.util.*
import java.util.function.ToIntFunction

internal class CachingSemiGraph<Node> private constructor(
  @JvmField val nodes: Collection<Node>,
  @JvmField val moduleToDirectDependencies: Map<Node, List<Node>>,
) : DFSTBuilder.DFSTBuilderAwareGraph<Node> {
  private val outs = IdentityHashMap<Node, MutableList<Node>>()

  init {
    val edges = Collections.newSetFromMap<Map.Entry<Node, Node>>(HashMap())
    for (node in nodes) {
      for (inNode in (moduleToDirectDependencies.get(node) ?: continue)) {
        if (edges.add(AbstractMap.SimpleImmutableEntry(inNode, node))) {
          // not a duplicate edge
          outs.computeIfAbsent(inNode) { ArrayList() }.add(node)
        }
      }
    }
  }

  companion object {
    fun createModuleGraph(plugins: List<IdeaPluginDescriptorImpl>): CachingSemiGraph<IdeaPluginDescriptorImpl> {
      val moduleMap = HashMap<String, IdeaPluginDescriptorImpl>(plugins.size * 2)
      val modules = ArrayList<IdeaPluginDescriptorImpl>(moduleMap.size)
      for (module in plugins) {
        moduleMap.put(module.pluginId.idString, module)
        for (v1Module in module.modules) {
          moduleMap.put(v1Module.idString, module)
        }

        modules.add(module)
        for (item in module.content.modules) {
          val subModule = item.requireDescriptor()
          modules.add(subModule)
          moduleMap.put(item.name, subModule)
        }
      }

      val hasAllModules = moduleMap.containsKey(PluginManagerCore.ALL_MODULES_MARKER.idString)
      val result: MutableSet<IdeaPluginDescriptorImpl> = Collections.newSetFromMap(IdentityHashMap())
      val moduleToDirectDependencies = IdentityHashMap<IdeaPluginDescriptorImpl, List<IdeaPluginDescriptorImpl>>(modules.size)
      for (module in modules) {
        val implicitDep = if (hasAllModules) getImplicitDependency(module, moduleMap) else null
        if (implicitDep != null) {
          if (module === implicitDep) {
            PluginManagerCore.getLogger().error("Plugin $module depends on self")
          }
          else {
            result.add(implicitDep)
          }
        }

        collectDirectDependenciesInOldFormat(module, moduleMap, result)
        collectDirectDependenciesInNewFormat(module, moduleMap, result)

        if (module.moduleName != null && module.pluginId != PluginManagerCore.CORE_ID) {
          // add main as implicit dependency
          val main = moduleMap.get(module.pluginId.idString)!!
          assert(main !== module)
          result.add(main)
        }

        if (!result.isEmpty()) {
          moduleToDirectDependencies.put(module, Java11Shim.INSTANCE.copyOfCollection(result))
          result.clear()
        }
      }
      return CachingSemiGraph(modules, moduleToDirectDependencies)
    }

    fun getTopologicalComparator(builder: DFSTBuilder<IdeaPluginDescriptorImpl>): Comparator<IdeaPluginDescriptorImpl> {
      val comparator = builder.comparator()
      // there is circular reference between core and implementation-detail plugin, as not all such plugins extracted from core,
      // so, ensure that core plugin is always first (otherwise not possible to register actions - parent group not defined)
      // don't use sortWith here - avoid loading kotlin stdlib
      return Comparator { o1, o2 ->
        when {
          o1.moduleName == null && o1.pluginId == PluginManagerCore.CORE_ID -> -1
          o2.moduleName == null && o2.pluginId == PluginManagerCore.CORE_ID -> 1
          else -> comparator.compare(o1, o2)
        }
      }
    }
  }

  fun getIn(node: Node): List<Node> = moduleToDirectDependencies.get(node) ?: Collections.emptyList()

  override fun buildOuts(nodeIndex: ToIntFunction<in Node>, node: Node): IntArray {
    val out = outs.get(node) ?: return ArrayUtilRt.EMPTY_INT_ARRAY
    return IntArray(out.size) {
      nodeIndex.applyAsInt(out.get(it))
    }
  }
}

/**
 * In 191.* and earlier builds Java plugin was part of the platform, so any plugin installed in IntelliJ IDEA might be able to use its
 * classes without declaring explicit dependency on the Java module. This method is intended to add implicit dependency on the Java plugin
 * for such plugins to avoid breaking compatibility with them.
 */
private fun getImplicitDependency(descriptor: IdeaPluginDescriptorImpl,
                                  idMap: Map<String, IdeaPluginDescriptorImpl>): IdeaPluginDescriptorImpl? {
  // skip our plugins as expected to be up-to-date whether bundled or not
  if (descriptor.isBundled || descriptor.packagePrefix != null || descriptor.implementationDetail) {
    return null
  }

  val pluginId = descriptor.pluginId
  if (PluginManagerCore.CORE_ID == pluginId || PluginManagerCore.JAVA_PLUGIN_ID == pluginId ||
      PluginManagerCore.hasModuleDependencies(descriptor)) {
    return null
  }

  // If a plugin does not include any module dependency tags in its plugin.xml, it's assumed to be a legacy plugin
  // and is loaded only in IntelliJ IDEA, so it may use classes from Java plugin.
  return idMap.get(PluginManagerCore.JAVA_MODULE_ID.idString)
}


private fun collectDirectDependenciesInOldFormat(rootDescriptor: IdeaPluginDescriptorImpl,
                                                 idMap: Map<String, IdeaPluginDescriptorImpl>,
                                                 result: MutableSet<IdeaPluginDescriptorImpl>) {
  for (dependency in rootDescriptor.pluginDependencies) {
    // check for missing optional dependency
    val dep = idMap.get(dependency.pluginId.idString) ?: continue
    if (dep.pluginId != PluginManagerCore.CORE_ID) {
      // ultimate plugin it is combined plugin, where some included XML can define dependency on ultimate explicitly and for now not clear,
      // can be such requirements removed or not
      if (rootDescriptor === dep) {
        if (rootDescriptor.pluginId != PluginManagerCore.CORE_ID) {
          PluginManagerCore.getLogger().error("Plugin $rootDescriptor depends on self")
        }
      }
      else {
        // e.g. `.env` plugin in an old format and doesn't explicitly specify dependency on a new extracted modules
        dep.content.modules.mapTo(result) { it.requireDescriptor() }

        result.add(dep)
      }
    }

    dependency.subDescriptor?.let {
      collectDirectDependenciesInOldFormat(it, idMap, result)
    }
  }

  for (moduleId in rootDescriptor.incompatibilities) {
    idMap.get(moduleId.idString)?.let {
      result.add(it)
    }
  }
}

private fun collectDirectDependenciesInNewFormat(module: IdeaPluginDescriptorImpl,
                                                 idMap: Map<String, IdeaPluginDescriptorImpl>,
                                                 result: MutableCollection<IdeaPluginDescriptorImpl>) {
  for (item in module.dependencies.modules) {
    val descriptor = idMap.get(item.name)
    if (descriptor != null) {
      result.add(descriptor)
    }
  }
  for (item in module.dependencies.plugins) {
    val descriptor = idMap.get(item.id.idString)
    // fake v1 module maybe located in a core plugin
    if (descriptor != null && descriptor.pluginId != PluginManagerCore.CORE_ID) {
      result.add(descriptor)
    }
  }
}