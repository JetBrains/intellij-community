// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet", "ReplaceNegatedIsEmptyWithIsNotEmpty")

package com.intellij.ide.plugins

import com.intellij.openapi.extensions.PluginId
import com.intellij.util.graph.DFSTBuilder
import com.intellij.util.graph.Graph
import org.jetbrains.annotations.ApiStatus
import java.util.*

@ApiStatus.Internal
class ModuleGraph internal constructor(
  @JvmField val topologicalComparator: Comparator<IdeaPluginDescriptorImpl>,
  private val modules: Collection<IdeaPluginDescriptorImpl>,
  private val directDependencies: Map<IdeaPluginDescriptorImpl, Collection<IdeaPluginDescriptorImpl>>,
  private val directDependents: Map<IdeaPluginDescriptorImpl, Collection<IdeaPluginDescriptorImpl>>,
) : Graph<IdeaPluginDescriptorImpl> {
  override fun getNodes(): Collection<IdeaPluginDescriptorImpl> = Collections.unmodifiableCollection(modules)

  fun getDependencies(descriptor: IdeaPluginDescriptorImpl): Collection<IdeaPluginDescriptorImpl> {
    return getOrEmpty(directDependencies, descriptor)
  }

  override fun getIn(descriptor: IdeaPluginDescriptorImpl): Iterator<IdeaPluginDescriptorImpl> = getDependencies(descriptor).iterator()

  fun getDependents(descriptor: IdeaPluginDescriptorImpl): Collection<IdeaPluginDescriptorImpl> = getOrEmpty(directDependents, descriptor)

  override fun getOut(descriptor: IdeaPluginDescriptorImpl): Iterator<IdeaPluginDescriptorImpl> = getDependents(descriptor).iterator()

  fun builder(): DFSTBuilder<IdeaPluginDescriptorImpl> = DFSTBuilder(this, null, true)

  internal fun sorted(builder: DFSTBuilder<IdeaPluginDescriptorImpl> = builder()): ModuleGraph {
    val topologicalComparator = toCoreAwareComparator(builder.comparator())
    return ModuleGraph(
      topologicalComparator = topologicalComparator,
      modules = modules.sortedWith(topologicalComparator),
      directDependencies = copySorted(directDependencies, topologicalComparator),
      directDependents = copySorted(directDependents, topologicalComparator)
    )
  }
}

private val VCS_ALIAS_ID = PluginId.getId("com.intellij.modules.vcs")
private val RIDER_ALIAS_ID = PluginId.getId("com.intellij.modules.rider")
private val COVERAGE_ALIAS_ID = PluginId.getId("com.intellij.modules.coverage")

internal fun createModuleGraph(plugins: Collection<IdeaPluginDescriptorImpl>): ModuleGraph {
  val moduleMap = HashMap<String, IdeaPluginDescriptorImpl>(plugins.size * 2)
  val modules = ArrayList<IdeaPluginDescriptorImpl>(moduleMap.size)
  for (module in plugins) {
    moduleMap.put(module.pluginId.idString, module)
    for (pluginAlias in module.pluginAliases) {
      moduleMap.put(pluginAlias.idString, module)
    }

    modules.add(module)
    for (item in module.content.modules) {
      val subModule = item.requireDescriptor()
      modules.add(subModule)
      moduleMap.put(item.name, subModule)
      for (pluginAlias in subModule.pluginAliases) {
        moduleMap.put(pluginAlias.idString, subModule)
      }
    }
  }

  val hasAllModules = moduleMap.containsKey(PluginManagerCore.ALL_MODULES_MARKER.idString)
  val result: MutableSet<IdeaPluginDescriptorImpl> = Collections.newSetFromMap(IdentityHashMap())
  val directDependencies = IdentityHashMap<IdeaPluginDescriptorImpl, List<IdeaPluginDescriptorImpl>>(modules.size)
  for (module in modules) {
    // If a plugin does not include any module dependency tags in its plugin.xml, it's assumed to be a legacy plugin
   // and is loaded only in IntelliJ IDEA, so it may use classes from Java plugin.
    val implicitDep = if (hasAllModules && isCheckingForImplicitDependencyNeeded(module)) moduleMap.get(PluginManagerCore.JAVA_MODULE_ID.idString) else null
    if (implicitDep != null) {
      if (module === implicitDep) {
        PluginManagerCore.logger.error("Plugin $module depends on self")
      }
      else {
        result.add(implicitDep)
      }
    }

    collectDirectDependenciesInOldFormat(module, moduleMap, result)
    collectDirectDependenciesInNewFormat(module, moduleMap, result)

    // Check modules as well, for example, intellij.diagram.impl.vcs.
    // We are not yet ready to recommend adding a dependency on extracted VCS modules since the coordinates are not finalized.
    if (module.pluginId != PluginManagerCore.CORE_ID || module.moduleName != null) {
      val strictCheck = module.isBundled || PluginManagerCore.isVendorJetBrains(module.vendor ?: "")
      if (!strictCheck || doesDependOnPluginAlias(module, VCS_ALIAS_ID)) {
        moduleMap.get("intellij.platform.vcs.impl")?.let { result.add(it) }
        moduleMap.get("intellij.platform.vcs.dvcs.impl")?.let { result.add(it) }
        moduleMap.get("intellij.platform.vcs.log.impl")?.let { result.add(it) }
      }
      if (!strictCheck) {
        if (System.getProperty("enable.implicit.json.dependency").toBoolean()) {
          moduleMap.get("com.intellij.modules.json")?.let { result.add(it) }
        }
        moduleMap.get("intellij.platform.collaborationTools")?.let { result.add(it) }
      }

      if (doesDependOnPluginAlias(module, RIDER_ALIAS_ID)) {
        moduleMap.get("intellij.rider")?.let { result.add(it) }
      }
      if (doesDependOnPluginAlias(module, COVERAGE_ALIAS_ID)) {
        moduleMap.get("intellij.platform.coverage")?.let { result.add(it) }
      }
    }

    if (module.moduleName != null && module.pluginId != PluginManagerCore.CORE_ID) {
      // add main as implicit dependency
      val main = moduleMap.get(module.pluginId.idString)!!
      assert(main !== module)
      result.add(main)
    }

    if (!result.isEmpty()) {
      directDependencies.put(module, result.toList())
      result.clear()
    }
  }

  val directDependents = IdentityHashMap<IdeaPluginDescriptorImpl, ArrayList<IdeaPluginDescriptorImpl>>(modules.size)
  val edges = HashSet<Map.Entry<IdeaPluginDescriptorImpl, IdeaPluginDescriptorImpl>>()
  for (module in modules) {
    for (inNode in getOrEmpty(directDependencies, module)) {
      if (edges.add(AbstractMap.SimpleImmutableEntry(inNode, module))) {
        // not a duplicate edge
        directDependents.computeIfAbsent(inNode) { ArrayList() }.add(module)
      }
    }
  }

  return ModuleGraph(
    topologicalComparator = Comparator { _, _ -> 0 },
    modules = modules,
    directDependencies = directDependencies,
    directDependents = directDependents,
  )
}

// alias in most cases points to Core plugin, so, we cannot use computed dependencies to check
private fun doesDependOnPluginAlias(plugin: IdeaPluginDescriptorImpl, @Suppress("SameParameterValue") aliasId: PluginId): Boolean {
  return plugin.pluginDependencies.any { it.pluginId == aliasId } || plugin.dependencies.plugins.any { it.id == aliasId }
}

private fun toCoreAwareComparator(comparator: Comparator<IdeaPluginDescriptorImpl>): Comparator<IdeaPluginDescriptorImpl> {
  // there is circular reference between core and implementation-detail plugin, as not all such plugins extracted from core,
  // so, ensure that core plugin is always first (otherwise not possible to register actions - a parent group not defined)
  // don't use sortWith here - avoid loading kotlin stdlib
  return Comparator { o1, o2 ->
    when {
      o1.moduleName == null && o1.pluginId == PluginManagerCore.CORE_ID -> -1
      o2.moduleName == null && o2.pluginId == PluginManagerCore.CORE_ID -> 1
      else -> comparator.compare(o1, o2)
    }
  }
}

private fun getOrEmpty(
  map: Map<IdeaPluginDescriptorImpl, Collection<IdeaPluginDescriptorImpl>>,
  descriptor: IdeaPluginDescriptorImpl,
): Collection<IdeaPluginDescriptorImpl> {
  return map.getOrDefault(descriptor, Collections.emptyList())
}

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

private val knownNotFullyMigratedPluginIds: Set<String> = hashSetOf(
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
    if (dep.pluginId != PluginManagerCore.CORE_ID || dep.moduleName != null) {
      // ultimate plugin it is combined plugin, where some included XML can define dependency on ultimate explicitly and for now not clear,
      // can be such requirements removed or not
      if (rootDescriptor === dep) {
        if (rootDescriptor.pluginId != PluginManagerCore.CORE_ID) {
          PluginManagerCore.logger.error("Plugin $rootDescriptor depends on self (${dependency})")
        }
      }
      else {
        // e.g. `.env` plugin in an old format and doesn't explicitly specify dependency on a new extracted modules
        dep.content.modules.mapTo(result) { it.requireDescriptor() }

        result.add(dep)
      }
    }

    if (knownNotFullyMigratedPluginIds.contains(rootDescriptor.pluginId.idString)) {
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