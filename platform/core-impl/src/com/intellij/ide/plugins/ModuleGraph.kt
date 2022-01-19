// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet", "ReplaceNegatedIsEmptyWithIsNotEmpty")

package com.intellij.ide.plugins

import com.intellij.util.graph.DFSTBuilder
import com.intellij.util.graph.Graph
import com.intellij.util.lang.Java11Shim
import org.jetbrains.annotations.ApiStatus
import java.util.*

@ApiStatus.Internal
interface ModuleGraph : Graph<IdeaPluginDescriptorImpl> {
  fun getDependencies(descriptor: IdeaPluginDescriptorImpl): Collection<IdeaPluginDescriptorImpl>

  fun getDependents(descriptor: IdeaPluginDescriptorImpl): Collection<IdeaPluginDescriptorImpl>
}

@ApiStatus.Internal
open class ModuleGraphBase protected constructor(
  private val modules: Collection<IdeaPluginDescriptorImpl>,
  private val directDependencies: Map<IdeaPluginDescriptorImpl, Collection<IdeaPluginDescriptorImpl>>,
  private val directDependents: Map<IdeaPluginDescriptorImpl, Collection<IdeaPluginDescriptorImpl>>,
) : ModuleGraph {
  override fun getNodes(): Collection<IdeaPluginDescriptorImpl> = Collections.unmodifiableCollection(modules)

  override fun getDependencies(descriptor: IdeaPluginDescriptorImpl): Collection<IdeaPluginDescriptorImpl> {
    return getOrEmpty(directDependencies, descriptor)
  }

  override fun getIn(descriptor: IdeaPluginDescriptorImpl): Iterator<IdeaPluginDescriptorImpl> = getDependencies(descriptor).iterator()

  override fun getDependents(descriptor: IdeaPluginDescriptorImpl): Collection<IdeaPluginDescriptorImpl> {
    return getOrEmpty(directDependents, descriptor)
  }

  override fun getOut(descriptor: IdeaPluginDescriptorImpl): Iterator<IdeaPluginDescriptorImpl> = getDependents(descriptor).iterator()

  fun builder() = DFSTBuilder(this, null, true)

  internal fun sorted(builder: DFSTBuilder<IdeaPluginDescriptorImpl> = builder()): SortedModuleGraph {
    return SortedModuleGraph(
      topologicalComparator = toCoreAwareComparator(builder.comparator()),
      modules = modules,
      directDependencies = directDependencies,
      directDependents = directDependents,
    )
  }
}

internal fun createModuleGraph(plugins: Collection<IdeaPluginDescriptorImpl>): ModuleGraphBase {
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
  val result = Collections.newSetFromMap<IdeaPluginDescriptorImpl>(IdentityHashMap())
  val directDependencies = IdentityHashMap<IdeaPluginDescriptorImpl, List<IdeaPluginDescriptorImpl>>(modules.size)
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
      directDependencies.put(module, Java11Shim.INSTANCE.copyOfCollection(result))
      result.clear()
    }
  }

  val directDependents = IdentityHashMap<IdeaPluginDescriptorImpl, ArrayList<IdeaPluginDescriptorImpl>>(modules.size)
  val edges = Collections.newSetFromMap<Map.Entry<IdeaPluginDescriptorImpl, IdeaPluginDescriptorImpl>>(HashMap())
  for (module in modules) {
    for (inNode in getOrEmpty(directDependencies, module)) {
      if (edges.add(AbstractMap.SimpleImmutableEntry(inNode, module))) {
        // not a duplicate edge
        directDependents.computeIfAbsent(inNode) { ArrayList() }.add(module)
      }
    }
  }

  return object : ModuleGraphBase(
    modules,
    directDependencies,
    directDependents,
  ) {}
}

private fun toCoreAwareComparator(comparator: Comparator<IdeaPluginDescriptorImpl>): Comparator<IdeaPluginDescriptorImpl> {
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

private fun getOrEmpty(map: Map<IdeaPluginDescriptorImpl, Collection<IdeaPluginDescriptorImpl>>,
                       descriptor: IdeaPluginDescriptorImpl): Collection<IdeaPluginDescriptorImpl> {
  return map.getOrDefault(descriptor, Collections.emptyList())
}

class SortedModuleGraph(
  val topologicalComparator: Comparator<IdeaPluginDescriptorImpl>,
  modules: Collection<IdeaPluginDescriptorImpl>,
  directDependencies: Map<IdeaPluginDescriptorImpl, Collection<IdeaPluginDescriptorImpl>>,
  directDependents: Map<IdeaPluginDescriptorImpl, Collection<IdeaPluginDescriptorImpl>>,
) : ModuleGraphBase(
  modules = modules.sortedWith(topologicalComparator),
  directDependencies = copySorted(directDependencies, topologicalComparator),
  directDependents = copySorted(directDependents, topologicalComparator)
)

private fun copySorted(
  map: Map<IdeaPluginDescriptorImpl, Collection<IdeaPluginDescriptorImpl>>,
  comparator: Comparator<IdeaPluginDescriptorImpl>,
): Map<IdeaPluginDescriptorImpl, List<IdeaPluginDescriptorImpl>> {
  val result = IdentityHashMap<IdeaPluginDescriptorImpl, List<IdeaPluginDescriptorImpl>>(map.size)
  for (element in map.entries) {
    result.put(element.key, element.value.sortedWith(comparator))
  }
  return result
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

val knownNotFullyMigratedPluginIds: Set<String> = hashSetOf(
  // Migration started with converting intellij.notebooks.visualization to a platform plugin, but adding a package prefix to Pythonid
  // or com.jetbrains.pycharm.ds.customization is a difficult task that can't be done by a single shot.
  "Pythonid",
  "com.jetbrains.pycharm.ds.customization",
)

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

    if (rootDescriptor.pluginId == PluginManagerCore.JAVA_PLUGIN_ID) {
        idMap.get(PluginManagerCore.CORE_ID.idString)!!.content.modules.firstOrNull { it.name == "intellij.platform.feedback" }?.let {
        result.add(it.requireDescriptor())
      }
    }
    else if (knownNotFullyMigratedPluginIds.contains(rootDescriptor.pluginId.idString)) {
      idMap.get(PluginManagerCore.CORE_ID.idString)!!.content.modules.mapTo(result) { it.requireDescriptor() }
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