// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceNegatedIsEmptyWithIsNotEmpty")

package com.intellij.ide.plugins

import com.intellij.core.CoreBundle
import com.intellij.diagnostic.PluginException
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.util.containers.Java11Shim
import com.intellij.util.graph.DFSTBuilder
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import org.jetbrains.annotations.ApiStatus
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
  private val enabledModuleV2Ids = HashMap<PluginModuleId, ContentModuleDescriptor>(unsortedPlugins.size * 2)

  internal fun checkPluginCycles(): List<PluginLoadingError> {
    if (builder.isAcyclic) {
      return emptyList()
    }

    val errors = ArrayList<PluginLoadingError>()
    for (component in builder.components) {
      if (component.size < 2) {
        continue
      }

      for (plugin in component) {
        plugin.isMarkedForLoading = false
      }

      val pluginString =
        component.joinToString(separator = ", ") { "'${it.name} (${it.pluginId.idString}${if (it.contentModuleName != null) ":" + it.contentModuleName else ""})'" }
      errors.add(PluginLoadingError(
        reason = null,
        htmlMessageSupplier = Supplier {
          val message = CoreBundle.message("plugin.loading.error.plugins.cannot.be.loaded.because.they.form.a.dependency.cycle", pluginString)
          HtmlChunk.text(message)
        },
        error = null,
      ))
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
    return errors
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
    initContext: PluginInitializationContext,
    disabler: ((descriptor: IdeaPluginDescriptorImpl, disabledModuleToProblematicPlugin: Map<PluginModuleId, PluginId>) -> Boolean)? = null,
  ): List<PluginNonLoadReason> {
    val logMessages = ArrayList<String>()
    val loadingErrors = ArrayList<PluginNonLoadReason>()
    val enabledRequiredContentModules = HashMap<PluginModuleId, ContentModuleDescriptor>()
    val disabledModuleToProblematicPlugin = HashMap<PluginModuleId, PluginId>()
    for (incompletePlugin in incompletePlugins) {
      incompletePlugin.contentModules.associateByTo(disabledModuleToProblematicPlugin, { it.moduleId }, { incompletePlugin.pluginId })
    }
    val usedPackagePrefixes = HashMap<String, IdeaPluginDescriptorImpl>()
    val isDisabledDueToPackagePrefixConflict = HashMap<PluginModuleId, IdeaPluginDescriptorImpl>()

    fun registerDependencyIsNotInstalledError(plugin: IdeaPluginDescriptorImpl, disabledModule: ContentModuleDescriptor) {
      loadingErrors.add(PluginDependencyIsNotInstalled(
        plugin = plugin,
        dependencyNameOrId = (disabledModuleToProblematicPlugin.get(disabledModule.moduleId) ?: disabledModule.parent.pluginId).idString,
        shouldNotifyUser = !plugin.isImplementationDetail,
      ))
    }

    fun markRequiredModulesAsDisabled(plugin: PluginMainDescriptor) {
      for (module in plugin.contentModules) {
        if (module.moduleLoadingRule.required && enabledRequiredContentModules.remove(module.moduleId) != null) {
          module.isMarkedForLoading = false
          logMessages.add("Module ${module.moduleId.name} is disabled because the containing plugin ${plugin.pluginId} won't be loaded")
        }
      }
    }

    m@ for (module in sortedModulesWithDependencies.modules) {
      if (module is ContentModuleDescriptor) {
        val envConfiguredModule = initContext.environmentConfiguredModules[module.moduleId]
        if (envConfiguredModule?.unavailabilityReason != null) {
          module.isMarkedForLoading = false
          logMessages.add(envConfiguredModule.unavailabilityReason.logMessage)
          continue
        }
      }

      if (module.useIdeaClassLoader && !canExtendIdeaClassLoader) {
        module.isMarkedForLoading = false
        logMessages.add("Module ${module.contentModuleName ?: module.pluginId} is not enabled because it uses deprecated `use-idea-classloader` attribute but PathClassLoader is disabled")
        continue@m
      }

      when (module) {
        is PluginMainDescriptor -> {
          if (module.pluginId != PluginManagerCore.CORE_ID && (!module.isMarkedForLoading || (disabler != null && disabler(module, disabledModuleToProblematicPlugin)))) {
            markRequiredModulesAsDisabled(module)
            continue
          }
        }
        is ContentModuleDescriptor -> {
          if (!module.isRequiredContentModule && !enabledPluginIds.containsKey(module.pluginId)) {
            disabledModuleToProblematicPlugin.put(module.moduleId, module.pluginId)
            continue
          }
        }
      }

      for (ref in module.moduleDependencies.modules) {
        val targetModule = enabledModuleV2Ids[ref] ?: enabledRequiredContentModules[ref]
        if (targetModule == null) {
          logMessages.add("Module ${module.contentModuleName ?: module.pluginId} is not enabled because dependency ${ref.name} is not available")
          when (module) {
            is ContentModuleDescriptor -> disabledModuleToProblematicPlugin.put(module.moduleId, disabledModuleToProblematicPlugin.get(ref) ?: PluginId.getId(ref.name))
            is PluginMainDescriptor -> markRequiredModulesAsDisabled(module)
          }
          continue@m
        }
        else {
          val visibilityError = checkVisibilityAndReturnErrorMessage(module, targetModule)
          if (visibilityError != null) {
            logMessages.add("Module ${module.contentModuleName ?: module.pluginId} is not enabled because $visibilityError")
            if (module is PluginMainDescriptor) {
              markRequiredModulesAsDisabled(module)
            }
          }
        }
      }
      for (ref in module.moduleDependencies.plugins) {
        if (!enabledPluginIds.containsKey(ref)) {
          logMessages.add("Module ${module.contentModuleName ?: module.pluginId} is not enabled because dependency ${ref} is not available")
          when (module) {
            is ContentModuleDescriptor -> disabledModuleToProblematicPlugin.put(module.moduleId, ref)
            is PluginMainDescriptor -> markRequiredModulesAsDisabled(module)
          }
          continue@m
        }
      }

      if (module.packagePrefix != null) {
        // do this as late as possible, because if we mark the module disabled a bit later, it would still be registered for a given prefix
        val alreadyRegistered = usedPackagePrefixes.putIfAbsent(module.packagePrefix, module)
        if (alreadyRegistered != null) {
          module.isMarkedForLoading = false
          if (module is ContentModuleDescriptor) {
            isDisabledDueToPackagePrefixConflict.put(module.moduleId, alreadyRegistered)
          }
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
              if (contentModule.moduleLoadingRule.required && !enabledRequiredContentModules.containsKey(contentModule.moduleId)) {
                module.isMarkedForLoading = false
                if (isDisabledDueToPackagePrefixConflict.containsKey(contentModule.moduleId)) {
                  val alreadyRegistered = isDisabledDueToPackagePrefixConflict[contentModule.moduleId]!!
                  loadingErrors.add(PluginPackagePrefixConflict(module, contentModule, alreadyRegistered))
                }
                else {
                  registerDependencyIsNotInstalledError(module, contentModule)
                }
                markRequiredModulesAsDisabled(module)
                continue@m
              }
            }
          }

          enabledPluginIds.put(module.pluginId, module)
          for (pluginAlias in module.pluginAliases) {
            enabledPluginIds.put(pluginAlias, module)
          }
          if (module.pluginId != PluginManagerCore.CORE_ID) {
            for (contentModule in module.contentModules) {
              if (contentModule.moduleLoadingRule.required) {
                val requiredContentModule = enabledRequiredContentModules.remove(contentModule.moduleId)!!
                markModuleAsEnabled(contentModule.moduleId, requiredContentModule)
              }
            }
          }
        }
        is ContentModuleDescriptor -> {
          if (module.isRequiredContentModule && module.pluginId != PluginManagerCore.CORE_ID) {
            enabledRequiredContentModules.put(module.moduleId, module)
          }
          else {
            markModuleAsEnabled(module.moduleId, module)
          }
        }
      }
    }

    val corePlugin = enabledPluginIds.get(PluginManagerCore.CORE_ID)
    if (corePlugin != null) {
      for (moduleItem in corePlugin.contentModules) {
        if (moduleItem.moduleLoadingRule.required && !enabledModuleV2Ids.containsKey(moduleItem.moduleId)) {
          moduleItem.isMarkedForLoading = false
          registerDependencyIsNotInstalledError(corePlugin, moduleItem)
        }
      }
    }

    if (!logMessages.isEmpty()) {
      PluginManagerCore.logger.info(logMessages.joinToString(separator = "\n", prefix = "Plugin set resolution:\n"))
    }
    return loadingErrors
  }

  private fun checkVisibilityAndReturnErrorMessage(sourceModule: PluginModuleDescriptor, targetModule: ContentModuleDescriptor): String? {
    if (pluginModuleVisibilityCheck == PluginModuleVisibilityCheckOption.DISABLED) {
      return null
    }

    val errorMessage = when (targetModule.visibility) {
      ModuleVisibility.PUBLIC -> null
      ModuleVisibility.INTERNAL -> {
        if (targetModule.parent.namespace != null && targetModule.parent.namespace == sourceModule.namespace) null
        else {
          val sourceNamespace = sourceModule.namespace?.let { "is from namespace '$it'" } ?: "has no namespace specified"
          val targetNamespace = targetModule.parent.namespace?.let { "namespace '$it'" } ?: "unspecified namespace"
          "it $sourceNamespace and depends on module '${targetModule.contentModuleName}' which is registered in '${targetModule.parent.pluginId}' plugin with internal visibility in $targetNamespace"
        }
      }
      ModuleVisibility.PRIVATE -> {
        if (sourceModule.pluginId == targetModule.pluginId) null
        else "it depends on module '${targetModule.contentModuleName}' which private visibility in '${targetModule.pluginId}' plugin"
      }
    }
    if (errorMessage == null) {
      return null
    }

    val sourceModuleId = sourceModule.contentModuleName ?: sourceModule.pluginId
    return when (pluginModuleVisibilityCheck) {
      PluginModuleVisibilityCheckOption.REPORT_WARNING -> {
        PluginManagerCore.logger.warn("$sourceModuleId has accessibility problem which is currently ignored: $errorMessage")
        null
      }
      PluginModuleVisibilityCheckOption.REPORT_ERROR -> {
        PluginManagerCore.logger.error(PluginException("$sourceModuleId isn't loaded: $errorMessage", sourceModule.pluginId))
        errorMessage
      }
      PluginModuleVisibilityCheckOption.DISABLED -> null
    }
  }

  private val PluginModuleDescriptor.namespace: String?
    get() = when (this) {
      is ContentModuleDescriptor -> parent.namespace
      is PluginMainDescriptor -> namespace
    }

  private fun markModuleAsEnabled(moduleId: PluginModuleId, moduleDescriptor: ContentModuleDescriptor) {
    enabledModuleV2Ids.put(moduleId, moduleDescriptor)
    for (pluginAlias in moduleDescriptor.pluginAliases) {
      enabledPluginIds.put(pluginAlias, moduleDescriptor)
    }
  }

  fun createPluginSetWithEnabledModulesMap(
    incompletePlugins: Collection<PluginMainDescriptor> = emptyList(),
    nonLoadReasonCollector: ArrayList<PluginNonLoadReason>? = null
  ): PluginSet {
    val nonLoadReasons = computeEnabledModuleMap(incompletePlugins = incompletePlugins, initContext = ProductPluginInitContext())
    nonLoadReasonCollector?.addAll(nonLoadReasons)
    return createPluginSet(incompletePlugins = incompletePlugins)
  }

  internal fun createPluginSet(incompletePlugins: Collection<PluginMainDescriptor>): PluginSet {
    val sortedPlugins = getSortedPlugins()
    // must be ordered
    val allPlugins = LinkedHashSet<PluginMainDescriptor>().also { result ->
      result.addAll(sortedPlugins)
      result.addAll(incompletePlugins)
    }

    fun isPluginModuleEnabled(module: PluginModuleDescriptor): Boolean {
      if (module !is ContentModuleDescriptor) {
        return module.isMarkedForLoading
      }
      return enabledModuleV2Ids.get(module.moduleId) === module
    }

    val java11Shim = Java11Shim.INSTANCE
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
      topologicalComparator = topologicalComparator,
    )
  }

  // use only for init plugins
  internal fun initEnableState(
    descriptor: IdeaPluginDescriptorImpl,
    idMap: Map<PluginId, IdeaPluginDescriptorImpl>,
    fullIdMap: Map<PluginId, IdeaPluginDescriptorImpl>,
    fullContentModuleIdMap: Map<PluginModuleId, ContentModuleDescriptor>,
    isPluginDisabled: (PluginId) -> Boolean,
    errors: MutableMap<PluginId, PluginNonLoadReason>,
    disabledModuleToProblematicPlugin: Map<PluginModuleId, PluginId>,
  ): PluginNonLoadReason? {
    val isNotifyUser = !descriptor.isImplementationDetail && !pluginRequiresUltimatePluginButItsDisabled(descriptor.pluginId, fullIdMap, fullContentModuleIdMap)
    for (incompatibleId in descriptor.incompatiblePlugins) {
      if (!enabledPluginIds.containsKey(incompatibleId) || isPluginDisabled(incompatibleId)) {
        continue
      }
      return PluginIsIncompatibleWithAnotherPlugin(plugin = descriptor, incompatiblePlugin = enabledPluginIds.get(incompatibleId)!!, shouldNotifyUser = isNotifyUser)
    }

    getAllPluginDependencies(descriptor)
      .firstOrNull { it !in enabledPluginIds }
      ?.let { dependencyPluginId ->
        return idMap.get(dependencyPluginId)?.let {
          // FIXME this is not precise reason type and may confuse user
          PluginDependencyIsDisabled(plugin = descriptor, dependencyId = it.pluginId, shouldNotifyUser = isNotifyUser)
        } ?: createCannotLoadError(descriptor, dependencyPluginId, errors, isNotifyUser)
      }

    val missingDependency = descriptor.moduleDependencies.modules.firstOrNull { !enabledModuleV2Ids.contains(it) } ?: return null

    val problematicPlugin = disabledModuleToProblematicPlugin.get(missingDependency)
    if (problematicPlugin != null && isPluginDisabled(problematicPlugin)) {
      return PluginDependencyIsDisabled(plugin = descriptor, dependencyId = problematicPlugin, shouldNotifyUser = isNotifyUser)
    }

    return PluginModuleDependencyCannotBeLoadedOrMissing(
      plugin = descriptor,
      moduleDependency = missingDependency,
      containingPlugin = problematicPlugin,
      shouldNotifyUser = isNotifyUser,
    )
  }
}

private enum class PluginModuleVisibilityCheckOption {
  /** No visibility checks performed */
  DISABLED,

  /** If a module depends on a module which is not visible to it, it's loaded and a warning is printed to the log */
  REPORT_WARNING,

  /** If a module depends on a module which is not visible to it, it's not loaded and an error is printed to the log */
  REPORT_ERROR,
}

private val pluginModuleVisibilityCheck by lazy {
  when (System.getProperty("intellij.platform.plugin.modules.check.visibility")) {
    "warning" -> PluginModuleVisibilityCheckOption.REPORT_WARNING
    "error" -> PluginModuleVisibilityCheckOption.REPORT_ERROR
    else -> PluginModuleVisibilityCheckOption.DISABLED
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
  if (dependency != null) {
    return PluginDependencyCannotBeLoaded(plugin = descriptor, dependency = dependency, shouldNotifyUser = isNotifyUser)
  }
  else {
    return PluginDependencyIsNotInstalled(plugin = descriptor, dependencyNameOrId = dependencyIdString, shouldNotifyUser = isNotifyUser)
  }
}

private fun getAllPluginDependencies(plugin: IdeaPluginDescriptorImpl): Sequence<PluginId> {
  return plugin.dependencies.asSequence()
           .filterNot { it.isOptional }
           .map { it.pluginId } +
         plugin.moduleDependencies.plugins.asSequence()
}