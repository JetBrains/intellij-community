// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceNegatedIsEmptyWithIsNotEmpty")

package com.intellij.ide.plugins

import com.intellij.core.CoreBundle
import com.intellij.openapi.extensions.PluginId
import com.intellij.util.Java11Shim
import com.intellij.util.graph.DFSTBuilder
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey
import java.util.*
import java.util.function.Supplier

@ApiStatus.Internal
class PluginSetBuilder(@JvmField val unsortedPlugins: Set<IdeaPluginDescriptorImpl>) {
  private val sortedModulesWithDependencies: ModulesWithDependencies
  private val builder: DFSTBuilder<IdeaPluginDescriptorImpl>
  val topologicalComparator: Comparator<IdeaPluginDescriptorImpl>

  init {
    val (unsortedModulesWithDependencies, additionalEdges) = createModulesWithDependenciesAndAdditionalEdges(unsortedPlugins)
    builder = DFSTBuilder(ModuleGraph(unsortedModulesWithDependencies, additionalEdges), null, true)
    topologicalComparator = toCoreAwareComparator(builder.comparator())
    sortedModulesWithDependencies = unsortedModulesWithDependencies.sorted(topologicalComparator)
  }
  
  private val enabledPluginIds = HashMap<PluginId, IdeaPluginDescriptorImpl>(unsortedPlugins.size)
  private val enabledModuleV2Ids = HashMap<String, IdeaPluginDescriptorImpl>(unsortedPlugins.size * 2)

  constructor(unsortedPlugins: Collection<IdeaPluginDescriptorImpl>) : this(LinkedHashSet(unsortedPlugins))

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
      val pluginToString: (IdeaPluginDescriptorImpl) -> String = { "id = ${it.pluginId.idString}@${it.moduleName} (${it.name})" }
      detailedMessage.append("Detected plugin dependencies cycle details (only related dependencies are included):\n")
      component
        .asSequence()
        .map { Pair(it, pluginToString(it)) }
        .sortedWith(Comparator.comparing({ it.second }, String.CASE_INSENSITIVE_ORDER))
        .forEach {
          detailedMessage.append("  ").append(it.second).append(" depends on:\n")
          (sortedModulesWithDependencies.directDependencies[it.first] ?: emptyList()).asSequence()
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
  private fun getSortedPlugins(): Array<IdeaPluginDescriptorImpl> {
    val pluginToNumber = Object2IntOpenHashMap<PluginId>(unsortedPlugins.size)
    pluginToNumber.put(PluginManagerCore.CORE_ID, 0)
    var number = 0
    for (module in sortedModulesWithDependencies.modules) {
      // no content, so will be no modules, add it
      if (module.descriptorPath != null || module.content.modules.isEmpty()) {
        pluginToNumber.putIfAbsent(module.pluginId, number++)
      }
    }
    val sorted = unsortedPlugins.toTypedArray()
    Arrays.sort(sorted, Comparator { o1, o2 ->
      pluginToNumber.getInt(o1.pluginId) - pluginToNumber.getInt(o2.pluginId)
    })
    return sorted
  }

  internal fun computeEnabledModuleMap(disabler: ((IdeaPluginDescriptorImpl) -> Boolean)? = null): List<PluginLoadingError> {
    val logMessages = ArrayList<String>()
    val loadingErrors = ArrayList<PluginLoadingError>()
    val enabledRequiredContentModules = HashMap<String, IdeaPluginDescriptorImpl>()
    val disabledModuleToProblematicPlugin = HashMap<String, PluginId>()
    val moduleIncompatibleWithCurrentMode = getModuleIncompatibleWithCurrentProductMode()
    val usedPackagePrefixes = HashMap<String, IdeaPluginDescriptorImpl>()
    val isDisabledDueToPackagePrefixConflict = HashMap<String, IdeaPluginDescriptorImpl>()

    fun registerLoadingError(plugin: IdeaPluginDescriptorImpl, disabledModule: PluginContentDescriptor.ModuleItem) {
      loadingErrors.add(createCannotLoadError(
        descriptor = plugin,
        dependencyPluginId = disabledModuleToProblematicPlugin.get(disabledModule.name) ?: PluginId.getId(disabledModule.name),
        errors = emptyMap(),
        isNotifyUser = !plugin.isImplementationDetail))
    }

    m@ for (module in sortedModulesWithDependencies.modules) {
      if (module.moduleName == moduleIncompatibleWithCurrentMode) {
        module.isMarkedForLoading = false
        logMessages.add("Module ${module.moduleName} is disabled because it is not compatible with the current product mode")
        continue
      }
      
      if (module.isUseIdeaClassLoader && !canExtendIdeaClassLoader) {
        module.isMarkedForLoading = false
        logMessages.add("Module ${module.moduleName ?: module.pluginId} is not enabled because it uses deprecated `use-idea-classloader` attribute but PathClassLoader is disabled")
        continue@m
      }

      if (module.moduleName == null) {
        if (module.pluginId != PluginManagerCore.CORE_ID && (!module.isMarkedForLoading || (disabler != null && disabler(module)))) {
          continue
        }
      }
      else if (!module.isRequiredContentModule && !enabledPluginIds.containsKey(module.pluginId)) {
        disabledModuleToProblematicPlugin.put(module.moduleName, module.pluginId)
        continue
      }

      for (ref in module.moduleDependencies.modules) {
        if (!enabledModuleV2Ids.containsKey(ref.name) && !enabledRequiredContentModules.containsKey(ref.name)) {
          logMessages.add("Module ${module.moduleName ?: module.pluginId} is not enabled because dependency ${ref.name} is not available")
          if (module.moduleName != null) {
            disabledModuleToProblematicPlugin.put(module.moduleName, disabledModuleToProblematicPlugin.get(ref.name) ?: PluginId.getId(ref.name))
          }
          continue@m
        }
      }
      for (ref in module.moduleDependencies.plugins) {
        if (!enabledPluginIds.containsKey(ref.id)) {
          logMessages.add("Module ${module.moduleName ?: module.pluginId} is not enabled because dependency ${ref.id} is not available")
          if (module.moduleName != null) {
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
          isDisabledDueToPackagePrefixConflict.put(module.moduleName ?: module.pluginId.idString, alreadyRegistered)
          logMessages.add("Module ${module.moduleName ?: module.pluginId} is not enabled because package prefix ${module.packagePrefix} is already used by " +
                          "${alreadyRegistered.moduleName ?: alreadyRegistered.pluginId}")
          loadingErrors.add(PluginLoadingError(
            module,
            detailedMessageSupplier = message("plugin.loading.error.long.package.prefix.conflict",
                                              module.name, alreadyRegistered.name,
                                              module.pluginId, alreadyRegistered.moduleName ?: alreadyRegistered.pluginId),
            shortMessageSupplier = message("plugin.loading.error.short.package.prefix.conflict",
                                           module.name, alreadyRegistered.name,
                                           module.pluginId, alreadyRegistered.moduleName ?: alreadyRegistered.pluginId),
            isNotifyUser = true,
          ))
          continue@m
        }
      }

      if (module.moduleName == null) {
        if (module.pluginId != PluginManagerCore.CORE_ID) {
          for (contentModule in module.content.modules) {
            if (contentModule.loadingRule.required && !enabledRequiredContentModules.containsKey(contentModule.name)) {
              module.isMarkedForLoading = false
              if (isDisabledDueToPackagePrefixConflict.containsKey(contentModule.name)) {
                val alreadyRegistered = isDisabledDueToPackagePrefixConflict[contentModule.name]!!
                loadingErrors.add(PluginLoadingError(
                  module,
                  detailedMessageSupplier = message("plugin.loading.error.long.package.prefix.conflict",
                                                    module.name, alreadyRegistered.name,
                                                    contentModule.name, alreadyRegistered.moduleName ?: alreadyRegistered.pluginId),
                  shortMessageSupplier = message("plugin.loading.error.short.package.prefix.conflict",
                                                 module.name, alreadyRegistered.name,
                                                 contentModule.name, alreadyRegistered.moduleName ?: alreadyRegistered.pluginId),
                  isNotifyUser = true,
                ))
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
          for (contentModule in module.content.modules) {
            if (contentModule.loadingRule.required) {
              val requiredContentModule = enabledRequiredContentModules.remove(contentModule.name)!!
              markModuleAsEnabled(contentModule.name, requiredContentModule)
            }
          }
        }
      }
      else if (module.isRequiredContentModule && module.pluginId != PluginManagerCore.CORE_ID) {
        enabledRequiredContentModules.put(module.moduleName, module)
      }
      else {
        markModuleAsEnabled(module.moduleName, module)
      }
    }

    val corePlugin = enabledPluginIds.get(PluginManagerCore.CORE_ID)
    if (corePlugin != null) {
      for (moduleItem in corePlugin.content.modules) {
        if (moduleItem.loadingRule.required && !enabledModuleV2Ids.containsKey(moduleItem.name)) {
          moduleItem.requireDescriptor().isMarkedForLoading = false
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
  private fun getModuleIncompatibleWithCurrentProductMode(): String? {
    return when (ProductLoadingStrategy.strategy.currentModeId) {
      /** intellij.platform.backend.split is currently available in 'monolith' mode because it's used as a backend in CodeWithMe */
      "monolith" -> "intellij.platform.frontend.split"
      "backend" -> "intellij.platform.frontend"
      "frontend" -> "intellij.platform.backend"
      else -> null
    }
  }

  private fun markModuleAsEnabled(moduleName: String, moduleDescriptor: IdeaPluginDescriptorImpl) {
    enabledModuleV2Ids.put(moduleName, moduleDescriptor)
    for (pluginAlias in moduleDescriptor.pluginAliases) {
      enabledPluginIds.put(pluginAlias, moduleDescriptor)
    }
  }

  fun createPluginSetWithEnabledModulesMap(): PluginSet {
    computeEnabledModuleMap()
    return createPluginSet(incompletePlugins = emptyList())
  }

  internal fun createPluginSet(incompletePlugins: Collection<IdeaPluginDescriptorImpl>): PluginSet {
    val sortedPlugins = getSortedPlugins()
    // must be ordered
    val allPlugins = LinkedHashSet<IdeaPluginDescriptorImpl>().also { result ->
      result.addAll(sortedPlugins)
      result.addAll(incompletePlugins)
    }

    val java11Shim = Java11Shim.INSTANCE
    fun isPluginModuleEnabled(module: IdeaPluginDescriptorImpl): Boolean {
      if (module.moduleName == null) return module.isMarkedForLoading
      return enabledModuleV2Ids[module.moduleName] === module
    }

    return PluginSet(
      sortedModulesWithDependencies = sortedModulesWithDependencies, 
      allPlugins = allPlugins,
      enabledPlugins = sortedPlugins.filterTo(ArrayList<IdeaPluginDescriptorImpl>()) { it.isMarkedForLoading },
      enabledModuleMap = java11Shim.copyOf(enabledModuleV2Ids),
      enabledPluginAndV1ModuleMap = java11Shim.copyOf(enabledPluginIds),
      enabledModules = ArrayList<IdeaPluginDescriptorImpl>().also { result ->
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
    isPluginDisabled: (PluginId) -> Boolean,
    errors: MutableMap<PluginId, PluginLoadingError>,
  ): PluginLoadingError? {
    val isNotifyUser = !descriptor.isImplementationDetail && !pluginRequiresUltimatePluginButItsDisabled(descriptor.pluginId, fullIdMap)
    for (incompatibleId in descriptor.incompatiblePlugins) {
      if (!enabledPluginIds.containsKey(incompatibleId) || isPluginDisabled(incompatibleId)) {
        continue
      }

      val presentableName = incompatibleId.idString
      return PluginLoadingError(
        plugin = descriptor,
        detailedMessageSupplier = message("plugin.loading.error.long.ide.contains.conflicting.module", descriptor.name, presentableName),
        shortMessageSupplier = message("plugin.loading.error.short.ide.contains.conflicting.module", presentableName),
        isNotifyUser = isNotifyUser,
      )
    }

    getAllPluginDependencies(descriptor)
      .firstOrNull { it !in enabledPluginIds }
      ?.let { dependencyPluginId ->
        return idMap.get(dependencyPluginId)?.let {
          createTransitivelyDisabledError(descriptor, it, isNotifyUser)
        } ?: createCannotLoadError(descriptor, dependencyPluginId, errors, isNotifyUser)
      }

    return descriptor.moduleDependencies.modules.asSequence().map { it.name }
      .firstOrNull { it !in enabledModuleV2Ids }
      ?.let {
        PluginLoadingError(
          plugin = descriptor,
          detailedMessageSupplier = message("plugin.loading.error.long.depends.on.not.installed.plugin", descriptor.name, it),
          shortMessageSupplier = message("plugin.loading.error.short.depends.on.not.installed.plugin", it),
          isNotifyUser = isNotifyUser,
        )
      }
  }
}

private fun createCannotLoadError(
  descriptor: IdeaPluginDescriptorImpl,
  dependencyPluginId: PluginId,
  errors: Map<PluginId, PluginLoadingError>,
  isNotifyUser: Boolean,
): PluginLoadingError {
  val dependencyIdString = dependencyPluginId.idString
  val dependency = errors.get(dependencyPluginId)?.plugin

  val detailedMessageSupplier = dependency?.let {
    message(
      "plugin.loading.error.long.depends.on.failed.to.load.plugin",
      descriptor.name,
      it.name ?: dependencyIdString,
    )
  } ?: message(
    "plugin.loading.error.long.depends.on.not.installed.plugin",
    descriptor.name,
    dependencyIdString,
  )

  val shortMessageSupplier = dependency?.let {
    message("plugin.loading.error.short.depends.on.failed.to.load.plugin", dependencyIdString)
  } ?: message("plugin.loading.error.short.depends.on.not.installed.plugin", dependencyIdString)

  return PluginLoadingError(
    plugin = descriptor,
    detailedMessageSupplier = detailedMessageSupplier,
    shortMessageSupplier = shortMessageSupplier,
    isNotifyUser = isNotifyUser,
  )
}

private fun createTransitivelyDisabledError(
  descriptor: IdeaPluginDescriptorImpl,
  dependency: IdeaPluginDescriptorImpl,
  isNotifyUser: Boolean,
): PluginLoadingError {
  val dependencyName = dependency.name
  return PluginLoadingError(
    plugin = descriptor,
    detailedMessageSupplier = message("plugin.loading.error.long.depends.on.disabled.plugin", descriptor.name, dependencyName),
    shortMessageSupplier = message("plugin.loading.error.short.depends.on.disabled.plugin", dependencyName),
    isNotifyUser = isNotifyUser,
    disabledDependency = dependency.pluginId,
  )
}

private fun message(key: @PropertyKey(resourceBundle = CoreBundle.BUNDLE) String, vararg params: Any): @Nls Supplier<String> {
  return Supplier { CoreBundle.message(key, *params) }
}

private fun getAllPluginDependencies(ideaPluginDescriptorImpl: IdeaPluginDescriptorImpl): Sequence<PluginId> {
  return ideaPluginDescriptorImpl.dependencies.asSequence()
           .filterNot { it.isOptional }
           .map { it.pluginId } +
         ideaPluginDescriptorImpl.moduleDependencies.plugins.asSequence()
           .map { it.id }
}