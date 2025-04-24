// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet", "ReplaceNegatedIsEmptyWithIsNotEmpty")

package com.intellij.ide.plugins

import com.intellij.openapi.extensions.PluginId
import com.intellij.util.Java11Shim
import java.util.*

private val VCS_ALIAS_ID = PluginId.getId("com.intellij.modules.vcs")
private val RIDER_ALIAS_ID = PluginId.getId("com.intellij.modules.rider")
private val COVERAGE_ALIAS_ID = PluginId.getId("com.intellij.modules.coverage")
private val ML_INLINE_ALIAS_ID = PluginId.getId("com.intellij.ml.inline.completion")
private val JSON_ALIAS_ID = PluginId.getId("com.intellij.modules.json")

internal class ModulesWithDependencies(val modules: List<IdeaPluginDescriptorImpl>,
                                       val directDependencies: Map<IdeaPluginDescriptorImpl, List<IdeaPluginDescriptorImpl>>) {
  internal fun sorted(topologicalComparator: Comparator<IdeaPluginDescriptorImpl>): ModulesWithDependencies {
    return ModulesWithDependencies(
      modules = modules.sortedWith(topologicalComparator),
      directDependencies = copySorted(directDependencies, topologicalComparator),
    )
  }
}

/**
 * Computes dependencies between modules in plugins and also computes additional edges in the module graph which shouldn't be treated as
 *  dependencies but should be used to determine the order in which modules are processed. 
 */
internal fun createModulesWithDependenciesAndAdditionalEdges(plugins: Collection<IdeaPluginDescriptorImpl>): Pair<ModulesWithDependencies, IdentityHashMap<IdeaPluginDescriptorImpl, List<IdeaPluginDescriptorImpl>>> {
  val moduleMap = HashMap<String, IdeaPluginDescriptorImpl>(plugins.size * 2)
  val modules = ArrayList<IdeaPluginDescriptorImpl>(moduleMap.size)
  val additionalEdges = IdentityHashMap<IdeaPluginDescriptorImpl, List<IdeaPluginDescriptorImpl>>()
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
  val dependenciesCollector: MutableSet<IdeaPluginDescriptorImpl> = Collections.newSetFromMap(IdentityHashMap())
  val additionalEdgesForCurrentModule: MutableSet<IdeaPluginDescriptorImpl> = Collections.newSetFromMap(IdentityHashMap())
  val directDependencies = IdentityHashMap<IdeaPluginDescriptorImpl, List<IdeaPluginDescriptorImpl>>(modules.size)
  for (module in modules) {
    // If a plugin does not include any module dependency tags in its plugin.xml, it's assumed to be a legacy plugin
   // and is loaded only in IntelliJ IDEA, so it may use classes from Java plugin.
    val implicitDep = if (hasAllModules && PluginCompatibilityUtils.isLegacyPluginWithoutPlatformAliasDependencies(module)) {
      moduleMap.get(PluginManagerCore.JAVA_MODULE_ID.idString)
    } else null
    if (implicitDep != null) {
      if (module === implicitDep) {
        PluginManagerCore.logger.error("Plugin $module depends on self")
      }
      else {
        dependenciesCollector.add(implicitDep)
      }
    }

    collectDirectDependenciesInOldFormat(module, moduleMap, dependenciesCollector)
    collectDirectDependenciesInNewFormat(module, moduleMap, dependenciesCollector, additionalEdgesForCurrentModule)

    // Check modules as well, for example, intellij.diagram.impl.vcs.
    // We are not yet ready to recommend adding a dependency on extracted VCS modules since the coordinates are not finalized.
    if (module.pluginId != PluginManagerCore.CORE_ID || module.moduleName != null) {
      val strictCheck = module.isBundled || PluginManagerCore.isVendorJetBrains(module.vendor ?: "")
      if (!strictCheck || doesDependOnPluginAlias(module, VCS_ALIAS_ID)) {
        moduleMap.get("intellij.platform.vcs.impl")?.let { dependenciesCollector.add(it) }
        moduleMap.get("intellij.platform.vcs.dvcs.impl")?.let { dependenciesCollector.add(it) }
        moduleMap.get("intellij.platform.vcs.log.impl")?.let { dependenciesCollector.add(it) }
      }
      if (!strictCheck) {
        if (System.getProperty("enable.implicit.json.dependency").toBoolean()) {
          moduleMap.get("com.intellij.modules.json")?.let { dependenciesCollector.add(it) }
          moduleMap.get("intellij.json.backend")?.let { dependenciesCollector.add(it) }
        }
        if (doesDependOnPluginAlias(module, JSON_ALIAS_ID)) {
          moduleMap.get("intellij.json.backend")?.let { dependenciesCollector.add(it) }
        }
        moduleMap.get("intellij.platform.collaborationTools")?.let { dependenciesCollector.add(it) }
      }

      /* Compatibility Layer */

      if (doesDependOnPluginAlias(module, RIDER_ALIAS_ID)) {
        moduleMap.get("intellij.rider")?.let { dependenciesCollector.add(it) }
      }
      if (doesDependOnPluginAlias(module, COVERAGE_ALIAS_ID)) {
        moduleMap.get("intellij.platform.coverage")?.let { dependenciesCollector.add(it) }
      }
      if (doesDependOnPluginAlias(module, ML_INLINE_ALIAS_ID)) {
        moduleMap.get("intellij.ml.inline.completion")?.let { dependenciesCollector.add(it) }
      }
      if (doesDependOnPluginAlias(module, PluginId.getId("org.jetbrains.completion.full.line"))) {
        moduleMap.get("intellij.fullLine.core")?.let { dependenciesCollector.add(it) }
        moduleMap.get("intellij.fullLine.local")?.let { dependenciesCollector.add(it) }
        moduleMap.get("intellij.fullLine.core.impl")?.let { dependenciesCollector.add(it) }
        moduleMap.get("intellij.ml.inline.completion")?.let { dependenciesCollector.add(it) }
      }
    }

    if (module.moduleName != null && module.pluginId != PluginManagerCore.CORE_ID) {
      // add main as an implicit dependency for optional content modules 
      val main = moduleMap.get(module.pluginId.idString)!!
      assert(main !== module)
      if (!module.isRequiredContentModule) {
        dependenciesCollector.add(main)
      }

      /* if the plugin containing the module is incompatible with some other plugins, make sure that the module is processed after that plugins (and all their required modules) 
         to ensure that the proper module is disabled in case of package conflict */
      for (incompatibility in main.incompatiblePlugins) {
        val incompatibleDescriptor = moduleMap.get(incompatibility.idString)
        if (incompatibleDescriptor != null) {
          additionalEdgesForCurrentModule.add(incompatibleDescriptor)
        }
      }
    }

    if (!additionalEdgesForCurrentModule.isEmpty()) {
      additionalEdgesForCurrentModule.removeAll(dependenciesCollector)
      if (!additionalEdgesForCurrentModule.isEmpty()) {
        additionalEdges.put(module, Java11Shim.INSTANCE.copyOfList(additionalEdgesForCurrentModule))
        additionalEdgesForCurrentModule.clear()
      }
    }
    if (!dependenciesCollector.isEmpty()) {
      directDependencies.put(module, Java11Shim.INSTANCE.copyOfList(dependenciesCollector))
      dependenciesCollector.clear()
    }
  }

  return ModulesWithDependencies(
    modules = modules,
    directDependencies = directDependencies,
  ) to additionalEdges
}

// alias in most cases points to Core plugin, so, we cannot use computed dependencies to check
private fun doesDependOnPluginAlias(plugin: IdeaPluginDescriptorImpl, @Suppress("SameParameterValue") aliasId: PluginId): Boolean {
  return plugin.dependencies.any { it.pluginId == aliasId } || plugin.moduleDependencies.plugins.any { it.id == aliasId }
}

internal fun toCoreAwareComparator(comparator: Comparator<IdeaPluginDescriptorImpl>): Comparator<IdeaPluginDescriptorImpl> {
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

private val knownNotFullyMigratedPluginIds: Set<String> = hashSetOf(
  // Migration started with converting intellij.notebooks.visualization to a platform plugin, but adding a package prefix to Pythonid
  // or com.jetbrains.pycharm.ds.customization is a challenging task that can't be done by a single shot.
  "Pythonid",
  "com.jetbrains.pycharm.ds.customization",
)

private fun collectDirectDependenciesInOldFormat(rootDescriptor: IdeaPluginDescriptorImpl,
                                                 idMap: Map<String, IdeaPluginDescriptorImpl>,
                                                 dependenciesCollector: MutableSet<IdeaPluginDescriptorImpl>) {
  for (dependency in rootDescriptor.dependencies) {
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
        dep.content.modules.mapTo(dependenciesCollector) { it.requireDescriptor() }

        dependenciesCollector.add(dep)
      }
    }

    if (knownNotFullyMigratedPluginIds.contains(rootDescriptor.pluginId.idString)) {
      idMap.get(PluginManagerCore.CORE_ID.idString)!!.content.modules.mapTo(dependenciesCollector) { it.requireDescriptor() }
    }

    dependency.subDescriptor?.let {
      collectDirectDependenciesInOldFormat(it, idMap, dependenciesCollector)
    }
  }

  for (moduleId in rootDescriptor.incompatiblePlugins) {
    idMap.get(moduleId.idString)?.let {
      dependenciesCollector.add(it)
    }
  }
}

private fun collectDirectDependenciesInNewFormat(
  module: IdeaPluginDescriptorImpl,
  idMap: Map<String, IdeaPluginDescriptorImpl>,
  dependenciesCollector: MutableCollection<IdeaPluginDescriptorImpl>,
  additionalEdges: MutableSet<IdeaPluginDescriptorImpl>
) {
  for (item in module.moduleDependencies.modules) {
    val dependency = idMap.get(item.name)
    if (dependency != null) {
      dependenciesCollector.add(dependency)
      if (dependency.isRequiredContentModule) {
        /* Add edges to all required plugin modules.
           This is needed to ensure that modules depending on a required content module are processed after all required content modules, because if a required module cannot be 
           loaded, the whole plugin will be disabled. */
        val dependencyPluginDescriptor = idMap.get(dependency.pluginId.idString)
        val currentPluginDescriptor = idMap.get(module.pluginId.idString)
        if (dependencyPluginDescriptor != null && dependencyPluginDescriptor !== currentPluginDescriptor) {
          additionalEdges.add(dependencyPluginDescriptor)
        }
      }
    }
  }
  for (item in module.moduleDependencies.plugins) {
    val descriptor = idMap.get(item.id.idString)
    // fake v1 module maybe located in a core plugin
    if (descriptor != null && descriptor.pluginId != PluginManagerCore.CORE_ID) {
      dependenciesCollector.add(descriptor)
    }
  }

  if (module.pluginId != PluginManagerCore.CORE_ID) {
    /* Add edges to all required content modules. 
       This is needed to ensure that the main plugin module is processed after them, and at that point we can determine whether the plugin 
       can be loaded or not. */
    for (item in module.content.modules) {
      if (item.loadingRule.required) {
        val descriptor = idMap.get(item.name)
        if (descriptor != null) {
          additionalEdges.add(descriptor)
        }
      }
    }
  }
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
