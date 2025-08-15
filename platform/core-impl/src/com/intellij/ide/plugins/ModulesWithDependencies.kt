// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet", "ReplaceNegatedIsEmptyWithIsNotEmpty")

package com.intellij.ide.plugins

import com.intellij.openapi.extensions.PluginId
import com.intellij.util.containers.Java11Shim
import org.jetbrains.annotations.ApiStatus
import java.util.*

private val VCS_ALIAS_ID = PluginId.getId("com.intellij.modules.vcs")
private val RIDER_ALIAS_ID = PluginId.getId("com.intellij.modules.rider")
private val COVERAGE_ALIAS_ID = PluginId.getId("com.intellij.modules.coverage")
private val ML_INLINE_ALIAS_ID = PluginId.getId("com.intellij.ml.inline.completion")
private val JSON_ALIAS_ID = PluginId.getId("com.intellij.modules.json")

internal class ModulesWithDependencies(val modules: List<PluginModuleDescriptor>,
                                       val directDependencies: Map<PluginModuleDescriptor, List<PluginModuleDescriptor>>) {
  internal fun sorted(topologicalComparator: Comparator<PluginModuleDescriptor>): ModulesWithDependencies {
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
internal fun createModulesWithDependenciesAndAdditionalEdges(plugins: Collection<PluginMainDescriptor>): Pair<ModulesWithDependencies, IdentityHashMap<PluginModuleDescriptor, List<PluginModuleDescriptor>>> {
  val moduleMap = HashMap<String, PluginModuleDescriptor>(plugins.size * 2)
  val modules = ArrayList<PluginModuleDescriptor>(moduleMap.size)
  val additionalEdges = IdentityHashMap<PluginModuleDescriptor, List<PluginModuleDescriptor>>()
  for (module in plugins) {
    moduleMap.put(module.pluginId.idString, module)
    for (pluginAlias in module.pluginAliases) {
      moduleMap.put(pluginAlias.idString, module)
    }

    modules.add(module)
    for (subModule in module.contentModules) {
      modules.add(subModule)
      moduleMap.put(subModule.moduleId.id, subModule) // FIXME module and plugin id namespaces should be separate
      for (pluginAlias in subModule.pluginAliases) {
        moduleMap.put(pluginAlias.idString, subModule)
      }
    }
  }

  val hasAllModules = moduleMap.containsKey(PluginManagerCore.ALL_MODULES_MARKER.idString)
  val dependenciesCollector: MutableSet<PluginModuleDescriptor> = Collections.newSetFromMap(IdentityHashMap())
  val additionalEdgesForCurrentModule: MutableSet<PluginModuleDescriptor> = Collections.newSetFromMap(IdentityHashMap())
  val directDependencies = IdentityHashMap<PluginModuleDescriptor, List<PluginModuleDescriptor>>(modules.size)
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

    collectDirectDependenciesInOldFormat(module, moduleMap, dependenciesCollector, additionalEdgesForCurrentModule)
    collectDirectDependenciesInNewFormat(module, moduleMap, dependenciesCollector, additionalEdgesForCurrentModule)

    // Check modules as well, for example, intellij.diagram.impl.vcs.
    // We are not yet ready to recommend adding a dependency on extracted VCS modules since the coordinates are not finalized.
    if (module.pluginId != PluginManagerCore.CORE_ID || module is ContentModuleDescriptor) {
      val strictCheck = module.isBundled || PluginManagerCore.isVendorJetBrains(module.vendor ?: "")
      if (!strictCheck || doesDependOnPluginAlias(module, VCS_ALIAS_ID)) {
        moduleMap.get("intellij.platform.vcs.impl")?.let { dependenciesCollector.add(it) }
        moduleMap.get("intellij.platform.vcs.dvcs")?.let { dependenciesCollector.add(it) }
        moduleMap.get("intellij.platform.vcs.dvcs.impl")?.let { dependenciesCollector.add(it) }
        moduleMap.get("intellij.platform.vcs.log")?.let { dependenciesCollector.add(it) }
        moduleMap.get("intellij.platform.vcs.log.graph")?.let { dependenciesCollector.add(it) }
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

    if (module.pluginId != PluginManagerCore.CORE_ID && module is ContentModuleDescriptor) {
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
  return plugin.dependencies.any { it.pluginId == aliasId } || plugin.moduleDependencies.plugins.any { it == aliasId }
}

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

/**
 * No new entries should be added to this set; if a plugin modules depends on content modules extracted from the core plugin, explicit dependencies on them should be added.
 * There is no need to fully convert the plugin to v2 for that.
 */
@ApiStatus.Obsolete
private val knownNotFullyMigratedPluginIds: Set<String> = hashSetOf(
  "com.jetbrains.pycharm.ds.customization", //todo remove this: DS-7102
)

/**
 * Specifies the list of content modules which was recently extracted from the main module of the core plugin and may have external usages.
 * Since such modules were loaded by the core classloader before, it wasn't necessary to specify any dependencies to use classes from them.
 * To avoid breaking compatibility, dependencies on these modules are automatically added to plugins which define dependency on the platform using 
 * `<depends>com.intellij.modules.platform</depends>` or `<depends>com.intellij.modules.lang</depends>` tags.
 * See [this article](https://youtrack.jetbrains.com/articles/IJPL-A-956#keep-compatibility-with-external-plugins) for more details.
 */
private val contentModulesExtractedInCorePluginWhichCanBeUsedFromExternalPlugins = listOf(
  "intellij.platform.collaborationTools.auth",
  "intellij.platform.collaborationTools.auth.base",
  "intellij.platform.tasks",
  "intellij.platform.tasks.impl",
  "intellij.platform.scriptDebugger.ui",
  "intellij.platform.scriptDebugger.backend",
  "intellij.platform.scriptDebugger.protocolReaderRuntime",
  "intellij.spellchecker.xml",
  "intellij.relaxng",
  "intellij.spellchecker",
)

private fun collectDirectDependenciesInOldFormat(
  rootDescriptor: IdeaPluginDescriptorImpl,
  idMap: Map<String, PluginModuleDescriptor>,
  dependenciesCollector: MutableSet<PluginModuleDescriptor>,
  additionalEdges: MutableSet<PluginModuleDescriptor>,
) {
  for (dependency in rootDescriptor.dependencies) {
    // check for missing optional dependency
    val dependencyPluginId = dependency.pluginId.idString
    val dep = idMap.get(dependencyPluginId) ?: continue
    if (dep.pluginId != PluginManagerCore.CORE_ID || dep is ContentModuleDescriptor) {
      // ultimate plugin it is combined plugin, where some included XML can define dependency on ultimate explicitly and for now not clear,
      // can be such requirements removed or not
      if (rootDescriptor === dep) {
        if (rootDescriptor.pluginId != PluginManagerCore.CORE_ID) {
          PluginManagerCore.logger.error("Plugin $rootDescriptor depends on self (${dependency})")
        }
      }
      else {
        // e.g. `.env` plugin in an old format and doesn't explicitly specify dependency on a new extracted modules
        dependenciesCollector.addAll(dep.contentModules)

        dependenciesCollector.add(dep)
      }
    }
    if (dependencyPluginId == "com.intellij.modules.platform" || dependencyPluginId == "com.intellij.modules.lang") {
      for (contentModuleId in contentModulesExtractedInCorePluginWhichCanBeUsedFromExternalPlugins) {
        idMap.get(contentModuleId)?.let {
          dependenciesCollector.add(it)
        }
      }
    }
    if (dep is ContentModuleDescriptor && dep.moduleLoadingRule.required) {
      val dependencyPluginDescriptor = idMap.get(dep.pluginId.idString)
      if (dependencyPluginDescriptor != null && dependencyPluginDescriptor !== rootDescriptor) {
        // Add an edge to the main module of the plugin. This is needed to ensure that this plugin is processed after it's decided whether to enable the referenced plugin or not.
        additionalEdges.add(dependencyPluginDescriptor)
      }
    }

    if (knownNotFullyMigratedPluginIds.contains(rootDescriptor.pluginId.idString)) {
      dependenciesCollector.addAll(idMap.get(PluginManagerCore.CORE_ID.idString)!!.contentModules)
    }

    dependency.subDescriptor?.let {
      collectDirectDependenciesInOldFormat(it, idMap, dependenciesCollector, additionalEdges)
    }
  }

  for (moduleId in rootDescriptor.incompatiblePlugins) {
    idMap.get(moduleId.idString)?.let {
      dependenciesCollector.add(it)
    }
  }
}

private fun collectDirectDependenciesInNewFormat(
  module: PluginModuleDescriptor,
  idMap: Map<String, PluginModuleDescriptor>,
  dependenciesCollector: MutableCollection<PluginModuleDescriptor>,
  additionalEdges: MutableSet<PluginModuleDescriptor>
) {
  for (item in module.moduleDependencies.modules) {
    val dependency = idMap.get(item.id)
    if (dependency != null) {
      dependenciesCollector.add(dependency)
      if (dependency.isRequiredContentModule) {
        // Add an edge to the main module of the plugin. This is needed to ensure that this module is processed after it's decided whether to enable the referenced plugin or not.
        val dependencyPluginDescriptor = idMap.get(dependency.pluginId.idString)
        val currentPluginDescriptor = idMap.get(module.pluginId.idString)
        if (dependencyPluginDescriptor != null && dependencyPluginDescriptor !== currentPluginDescriptor) {
          additionalEdges.add(dependencyPluginDescriptor)
        }
      }
    }
  }
  for (item in module.moduleDependencies.plugins) {
    val targetModule = idMap.get(item.idString)
    // fake v1 module maybe located in a core plugin
    if (targetModule != null && (targetModule is ContentModuleDescriptor || targetModule.pluginId != PluginManagerCore.CORE_ID)) {
      dependenciesCollector.add(targetModule)
    }
  }

  if (module.pluginId != PluginManagerCore.CORE_ID) {
    /* Add edges to all required content modules. 
       This is needed to ensure that the main plugin module is processed after them, and at that point we can determine whether the plugin 
       can be loaded or not. */
    for (item in module.contentModules) {
      if (item.moduleLoadingRule.required) {
        val descriptor = idMap.get(item.moduleId.id) // FIXME module and plugin id namespaces should be separate
        if (descriptor != null) {
          additionalEdges.add(descriptor)
        }
      }
    }
  }
}

private fun copySorted(
  map: Map<PluginModuleDescriptor, Collection<PluginModuleDescriptor>>,
  comparator: Comparator<PluginModuleDescriptor>,
): Map<PluginModuleDescriptor, List<PluginModuleDescriptor>> {
  val result = IdentityHashMap<PluginModuleDescriptor, List<PluginModuleDescriptor>>(map.size)
  for (element in map.entries) {
    result.put(element.key, element.value.sortedWith(comparator))
  }
  return result
}
