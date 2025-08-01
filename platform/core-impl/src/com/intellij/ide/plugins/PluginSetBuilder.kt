// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceNegatedIsEmptyWithIsNotEmpty")

package com.intellij.ide.plugins

import com.intellij.core.CoreBundle
import com.intellij.openapi.extensions.PluginId
import com.intellij.util.containers.Java11Shim
import com.intellij.util.graph.DFSTBuilder
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey
import java.util.*
import java.util.function.Supplier

@ApiStatus.Internal
class PluginSetBuilder(@JvmField val unsortedPlugins: Set<PluginMainDescriptor>) {
  private val moduleGraph: ModuleGraph
  private val sortedModulesWithDependencies: ModulesWithDependencies
  private val builder: DFSTBuilder<PluginModuleDescriptor>
  val topologicalComparator: Comparator<PluginModuleDescriptor>

  init {
    val (unsortedModulesWithDependencies, additionalEdges) = createModulesWithDependenciesAndAdditionalEdges(unsortedPlugins)
    moduleGraph = ModuleGraph(unsortedModulesWithDependencies, additionalEdges)
    builder = DFSTBuilder(moduleGraph, null, true)
    topologicalComparator = toCoreAwareComparator(builder.comparator())
    sortedModulesWithDependencies = unsortedModulesWithDependencies.sorted(topologicalComparator)
  }
  
  private val enabledPluginIds = HashMap<PluginId, PluginModuleDescriptor>(unsortedPlugins.size)
  private val enabledModuleV2Ids = HashMap<String, PluginModuleDescriptor>(unsortedPlugins.size * 2)

  internal fun checkPluginCycles(errors: MutableList<Supplier<String>>) {
    if (builder.isAcyclic) {
      return
    }

    for (component in builder.components) {
      if (component.size < 2) {
        continue
      }

      for (plugin in component) {
        plugin.isMarkedForLoading = false
      }

      val pluginString = component.joinToString(separator = ", ") { "'${it.name}'" }
      errors.add(message("plugin.loading.error.plugins.cannot.be.loaded.because.they.form.a.dependency.cycle", pluginString))
      val detailedMessage = StringBuilder()
      val pluginToString: (IdeaPluginDescriptorImpl) -> String = { "id = ${it.pluginId.idString}@${it.contentModuleName} (${it.name})" }
      detailedMessage.append("Detected plugin dependencies cycle details (only related dependencies are included):\n")
      component
        .asSequence()
        .map { Pair(it, pluginToString(it)) }
        .sortedWith(Comparator.comparing({ it.second }, String.CASE_INSENSITIVE_ORDER))
        .forEach {
          detailedMessage.append("  ").append(it.second).append(" depends on:\n")
          moduleGraph.getIn(it.first).asSequence()
            .filter { o: IdeaPluginDescriptorImpl -> component.contains(o) }
            .map(pluginToString)
            .sortedWith(java.lang.String.CASE_INSENSITIVE_ORDER)
            .forEach { dep: String? ->
              detailedMessage.append("    ").append(dep).append("\n")
            }
        }
      PluginManagerCore.logger.info(detailedMessage.toString())
    }
  }

  // Only plugins returned. Not modules. See PluginManagerTest.moduleSort test to understand the issue.
  private fun getSortedPlugins(): Array<PluginMainDescriptor> {
    val pluginToNumber = Object2IntOpenHashMap<PluginId>(unsortedPlugins.size)
    pluginToNumber.put(PluginManagerCore.CORE_ID, 0)
    var number = 0
    for (module in sortedModulesWithDependencies.modules) {
      // no content, so will be no modules, add it
      if (module.descriptorPath != null || module.contentModules.isEmpty()) {
        pluginToNumber.putIfAbsent(module.pluginId, number++)
      }
    }
    val sorted = unsortedPlugins.toTypedArray()
    Arrays.sort(sorted, Comparator { o1, o2 ->
      pluginToNumber.getInt(o1.pluginId) - pluginToNumber.getInt(o2.pluginId)
    })
    return sorted
  }

  internal fun computeEnabledModuleMap(
    incompletePlugins: Collection<PluginMainDescriptor>,
    currentProductModeEvaluator: () -> String = { ProductLoadingStrategy.strategy.currentModeId },
    disabler: ((descriptor: IdeaPluginDescriptorImpl, disabledModuleToProblematicPlugin: Map<String, PluginId>) -> Boolean)? = null,
  ): List<PluginNonLoadReason> {
    val logMessages = ArrayList<String>()
    val loadingErrors = ArrayList<PluginNonLoadReason>()
    val enabledRequiredContentModules = HashMap<String, ContentModuleDescriptor>()
    val disabledModuleToProblematicPlugin = HashMap<String, PluginId>()
    for (incompletePlugin in incompletePlugins) {
      incompletePlugin.contentModules.associateByTo(disabledModuleToProblematicPlugin, { it.moduleName }, { incompletePlugin.pluginId })
    }
    val moduleIncompatibleWithCurrentMode = getModuleIncompatibleWithCurrentProductMode(currentProductModeEvaluator)
    val usedPackagePrefixes = HashMap<String, IdeaPluginDescriptorImpl>()
    val isDisabledDueToPackagePrefixConflict = HashMap<String, IdeaPluginDescriptorImpl>()

    fun registerLoadingError(plugin: IdeaPluginDescriptorImpl, disabledModule: ContentModuleDescriptor) {
      loadingErrors.add(createCannotLoadError(
        descriptor = plugin,
        dependencyPluginId = disabledModuleToProblematicPlugin.get(disabledModule.moduleName) ?: PluginId.getId(disabledModule.moduleName),
        errors = emptyMap(),
        isNotifyUser = !plugin.isImplementationDetail))
    }

    m@ for (module in sortedModulesWithDependencies.modules) {
      if (module is ContentModuleDescriptor && module.moduleName == moduleIncompatibleWithCurrentMode) {
        module.isMarkedForLoading = false
        logMessages.add("Module ${module.moduleName} is disabled because it is not compatible with the current product mode")
        continue
      }

      if (module.useIdeaClassLoader && !canExtendIdeaClassLoader) {
        module.isMarkedForLoading = false
        logMessages.add("Module ${module.contentModuleName ?: module.pluginId} is not enabled because it uses deprecated `use-idea-classloader` attribute but PathClassLoader is disabled")
        continue@m
      }

      if (module !is ContentModuleDescriptor) {
        if (module.pluginId != PluginManagerCore.CORE_ID && (!module.isMarkedForLoading || (disabler != null && disabler(module, disabledModuleToProblematicPlugin)))) {
          continue
        }
      }
      else if (!module.isRequiredContentModule && !enabledPluginIds.containsKey(module.pluginId)) {
        disabledModuleToProblematicPlugin.put(module.moduleName, module.pluginId)
        continue
      }

      for (ref in module.moduleDependencies.modules) {
        if (!enabledModuleV2Ids.containsKey(ref.name) && !enabledRequiredContentModules.containsKey(ref.name)) {
          logMessages.add("Module ${module.contentModuleName ?: module.pluginId} is not enabled because dependency ${ref.name} is not available")
          if (module is ContentModuleDescriptor) {
            disabledModuleToProblematicPlugin.put(module.moduleName, disabledModuleToProblematicPlugin.get(ref.name) ?: PluginId.getId(ref.name))
          }
          continue@m
        }
      }
      for (ref in module.moduleDependencies.plugins) {
        if (!enabledPluginIds.containsKey(ref.id)) {
          logMessages.add("Module ${module.contentModuleName ?: module.pluginId} is not enabled because dependency ${ref.id} is not available")
          if (module is ContentModuleDescriptor) {
            disabledModuleToProblematicPlugin.put(module.moduleName, ref.id)
          }
          continue@m
        }
      }

      if (module.packagePrefix != null) {
        // do this as late as possible, because if we mark the module disabled a bit later, it would still be registered for a given prefix
        val alreadyRegistered = usedPackagePrefixes.putIfAbsent(module.packagePrefix, module)
        if (alreadyRegistered != null) {
          module.isMarkedForLoading = false
          isDisabledDueToPackagePrefixConflict.put(module.contentModuleName ?: module.pluginId.idString, alreadyRegistered)
          logMessages.add("Module ${module.contentModuleName ?: module.pluginId} is not enabled because package prefix ${module.packagePrefix} is already used by " +
                          "${alreadyRegistered.contentModuleName ?: alreadyRegistered.pluginId}")
          loadingErrors.add(PluginPackagePrefixConflict(module, module, alreadyRegistered))
          continue@m
        }
      }

      when (module) {
        is PluginMainDescriptor -> {
          if (module.pluginId != PluginManagerCore.CORE_ID) {
            for (contentModule in module.contentModules) {
              if (contentModule.moduleLoadingRule.required && !enabledRequiredContentModules.containsKey(contentModule.moduleName)) {
                module.isMarkedForLoading = false
                if (isDisabledDueToPackagePrefixConflict.containsKey(contentModule.moduleName)) {
                  val alreadyRegistered = isDisabledDueToPackagePrefixConflict[contentModule.moduleName]!!
                  loadingErrors.add(PluginPackagePrefixConflict(module, contentModule, alreadyRegistered))
                } else {
                  registerLoadingError(module, contentModule)
                }
                continue@m
              }
            }
          }

          enabledPluginIds.put(module.pluginId, module)
          for (pluginAlias in module.pluginAliases) {
            enabledPluginIds.put(pluginAlias, module)
          }
          if (module.packagePrefix != null) {
            enabledModuleV2Ids.put(module.pluginId.idString, module)
          }
          if (module.pluginId != PluginManagerCore.CORE_ID) {
            for (contentModule in module.contentModules) {
              if (contentModule.moduleLoadingRule.required) {
                val requiredContentModule = enabledRequiredContentModules.remove(contentModule.moduleName)!!
                markModuleAsEnabled(contentModule.moduleName, requiredContentModule)
              }
            }
          }
        }
        is ContentModuleDescriptor -> {
          if (module.isRequiredContentModule && module.pluginId != PluginManagerCore.CORE_ID) {
            enabledRequiredContentModules.put(module.moduleName, module)
          }
          else {
            markModuleAsEnabled(module.moduleName, module)
          }
        }
      }
    }

    val corePlugin = enabledPluginIds.get(PluginManagerCore.CORE_ID)
    if (corePlugin != null) {
      for (moduleItem in corePlugin.contentModules) {
        if (moduleItem.moduleLoadingRule.required && !enabledModuleV2Ids.containsKey(moduleItem.moduleName)) {
          moduleItem.isMarkedForLoading = false
          registerLoadingError(corePlugin, moduleItem)
        }
      }
    }
    
    if (!logMessages.isEmpty()) {
      PluginManagerCore.logger.info(logMessages.joinToString(separator = "\n", prefix = "Plugin set resolution:\n"))
    }
    return loadingErrors
  }

  /**
   * Returns a module which should be disabled because it's not relevant to the current com.intellij.platform.runtime.product.ProductMode.
   * All modules that depend on the specified module will be automatically disabled as well.
   */
  private fun getModuleIncompatibleWithCurrentProductMode(currentProductModeEvaluator: () -> String): String? {
    return when (currentProductModeEvaluator()) {
      /** intellij.platform.backend.split is currently available in 'monolith' mode because it's used as a backend in CodeWithMe */
      "monolith" -> "intellij.platform.frontend.split"
      "backend" -> "intellij.platform.frontend"
      "frontend" -> "intellij.platform.backend"
      else -> null
    }
  }

  private fun markModuleAsEnabled(moduleName: String, moduleDescriptor: ContentModuleDescriptor) {
    enabledModuleV2Ids.put(moduleName, moduleDescriptor)
    for (pluginAlias in moduleDescriptor.pluginAliases) {
      enabledPluginIds.put(pluginAlias, moduleDescriptor)
    }
  }

  fun createPluginSetWithEnabledModulesMap(): PluginSet {
    //TODO pass proper list of incomplete plugins to ensure that this information isn't lost after enabling/disabling a plugin dynamically
    val incompletePlugins = emptyList<PluginMainDescriptor>()
    computeEnabledModuleMap(incompletePlugins = incompletePlugins)
    return createPluginSet(incompletePlugins = incompletePlugins)
  }

  internal fun createPluginSet(incompletePlugins: Collection<PluginMainDescriptor>): PluginSet {
    val sortedPlugins = getSortedPlugins()
    // must be ordered
    val allPlugins = LinkedHashSet<PluginMainDescriptor>().also { result ->
      result.addAll(sortedPlugins)
      result.addAll(incompletePlugins)
    }

    val java11Shim = Java11Shim.INSTANCE
    fun isPluginModuleEnabled(module: PluginModuleDescriptor): Boolean {
      if (module !is ContentModuleDescriptor) return module.isMarkedForLoading
      return enabledModuleV2Ids[module.moduleName] === module
    }

    return PluginSet(
      sortedModulesWithDependencies = sortedModulesWithDependencies, 
      allPlugins = allPlugins,
      enabledPlugins = sortedPlugins.filterTo(ArrayList<PluginMainDescriptor>()) { it.isMarkedForLoading },
      enabledModuleMap = java11Shim.copyOf(enabledModuleV2Ids),
      enabledPluginAndV1ModuleMap = java11Shim.copyOf(enabledPluginIds),
      enabledModules = ArrayList<PluginModuleDescriptor>().also { result ->
        for (module in sortedModulesWithDependencies.modules) {
          if (isPluginModuleEnabled(module)) {
            result.add(module)
          }
        }
      },
    )
  }

  // use only for init plugins
  internal fun initEnableState(
    descriptor: IdeaPluginDescriptorImpl,
    idMap: Map<PluginId, IdeaPluginDescriptorImpl>,
    fullIdMap: Map<PluginId, IdeaPluginDescriptorImpl>,
    fullContentModuleIdMap: Map<String, ContentModuleDescriptor>,
    isPluginDisabled: (PluginId) -> Boolean,
    errors: MutableMap<PluginId, PluginNonLoadReason>,
    disabledModuleToProblematicPlugin: Map<String, PluginId>,
  ): PluginNonLoadReason? {
    val isNotifyUser = !descriptor.isImplementationDetail && !pluginRequiresUltimatePluginButItsDisabled(descriptor.pluginId, fullIdMap, fullContentModuleIdMap)
    for (incompatibleId in descriptor.incompatiblePlugins) {
      if (!enabledPluginIds.containsKey(incompatibleId) || isPluginDisabled(incompatibleId)) {
        continue
      }
      return PluginIsIncompatibleWithAnotherPlugin(descriptor, enabledPluginIds[incompatibleId]!!, isNotifyUser)
    }

    getAllPluginDependencies(descriptor)
      .firstOrNull { it !in enabledPluginIds }
      ?.let { dependencyPluginId ->
        return idMap.get(dependencyPluginId)?.let {
          // FIXME this is not precise reason type and may confuse user
          PluginDependencyIsDisabled(descriptor, it.pluginId, isNotifyUser)
        } ?: createCannotLoadError(descriptor, dependencyPluginId, errors, isNotifyUser)
      }

    val missingDependency = descriptor.moduleDependencies.modules
      .firstOrNull { it.name !in enabledModuleV2Ids }
    if (missingDependency != null) {
      val problematicPlugin = disabledModuleToProblematicPlugin[missingDependency.name]
      if (problematicPlugin != null && isPluginDisabled(problematicPlugin)) {
        return PluginDependencyIsDisabled(plugin = descriptor, dependencyId = problematicPlugin, shouldNotifyUser = isNotifyUser)
      }
      return PluginModuleDependencyCannotBeLoadedOrMissing(plugin = descriptor, moduleDependency = missingDependency, containingPlugin = problematicPlugin, shouldNotifyUser = isNotifyUser)
    }
    return null
  }
}

private fun createCannotLoadError(
  descriptor: IdeaPluginDescriptorImpl,
  dependencyPluginId: PluginId,
  errors: Map<PluginId, PluginNonLoadReason>,
  isNotifyUser: Boolean,
): PluginNonLoadReason {
  val dependencyIdString = dependencyPluginId.idString
  val dependency = errors.get(dependencyPluginId)?.plugin
  return if (dependency != null) {
    PluginDependencyCannotBeLoaded(descriptor, dependency.name ?: dependencyIdString, isNotifyUser)
  } else {
    PluginDependencyIsNotInstalled(descriptor, dependencyIdString, isNotifyUser)
  }
}

private fun message(key: @PropertyKey(resourceBundle = CoreBundle.BUNDLE) String, vararg params: Any): @Nls Supplier<String> {
  return Supplier { CoreBundle.message(key, *params) }
}

private fun getAllPluginDependencies(plugin: IdeaPluginDescriptorImpl): Sequence<PluginId> {
  return plugin.dependencies.asSequence()
           .filterNot { it.isOptional }
           .map { it.pluginId } +
         plugin.moduleDependencies.plugins.asSequence()
           .map { it.id }
}