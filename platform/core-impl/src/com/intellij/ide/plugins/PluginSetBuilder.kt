// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceNegatedIsEmptyWithIsNotEmpty")

package com.intellij.ide.plugins

import com.intellij.core.CoreBundle
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.util.Java11Shim
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey
import java.util.*
import java.util.function.Predicate
import java.util.function.Supplier

@ApiStatus.Internal
class PluginSetBuilder(
  val unsortedPlugins: Set<IdeaPluginDescriptorImpl>,
) {

  private val _moduleGraph = createModuleGraph(unsortedPlugins)
  private val builder = _moduleGraph.builder()
  val moduleGraph: SortedModuleGraph = _moduleGraph.sorted(builder)

  private val enabledPluginIds = HashMap<PluginId, IdeaPluginDescriptorImpl>(unsortedPlugins.size)
  private val enabledModuleV2Ids = HashMap<String, IdeaPluginDescriptorImpl>(unsortedPlugins.size * 2)

  constructor(unsortedPlugins: Collection<IdeaPluginDescriptorImpl>) : this(LinkedHashSet(unsortedPlugins))

  fun checkPluginCycles(errors: MutableList<Supplier<String>>) {
    if (builder.isAcyclic) {
      return
    }

    for (component in builder.components) {
      if (component.size < 2) {
        continue
      }

      for (plugin in component) {
        plugin.isEnabled = false
      }

      val pluginsString = component.joinToString(separator = ", ") { "'${it.name}'" }
      errors.add(message("plugin.loading.error.plugins.cannot.be.loaded.because.they.form.a.dependency.cycle", pluginsString))
      val detailedMessage = StringBuilder()
      val pluginToString: (IdeaPluginDescriptorImpl) -> String = { "id = ${it.pluginId.idString} (${it.name})" }
      detailedMessage.append("Detected plugin dependencies cycle details (only related dependencies are included):\n")
      component
        .asSequence()
        .map { Pair(it, pluginToString(it)) }
        .sortedWith(Comparator.comparing({ it.second }, String.CASE_INSENSITIVE_ORDER))
        .forEach {
          detailedMessage.append("  ").append(it.second).append(" depends on:\n")
          moduleGraph.getDependencies(it.first).asSequence()
            .filter { o: IdeaPluginDescriptorImpl -> component.contains(o) }
            .map(pluginToString)
            .sortedWith(java.lang.String.CASE_INSENSITIVE_ORDER)
            .forEach { dep: String? ->
              detailedMessage.append("    ").append(dep).append("\n")
            }
        }
      PluginManagerCore.getLogger().info(detailedMessage.toString())
    }
  }

  // Only plugins returned. Not modules. See PluginManagerTest.moduleSort test to understand the issue.
  private fun getSortedPlugins(): Array<IdeaPluginDescriptorImpl> {
    val pluginToNumber = Object2IntOpenHashMap<PluginId>(unsortedPlugins.size)
    pluginToNumber.put(PluginManagerCore.CORE_ID, 0)
    var number = 0
    for (module in moduleGraph.nodes) {
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

  private fun getEnabledModules(): List<IdeaPluginDescriptorImpl> {
    val result = ArrayList<IdeaPluginDescriptorImpl>(moduleGraph.nodes.size)
    for (module in moduleGraph.nodes) {
      if (if (module.moduleName == null) module.isEnabled else enabledModuleV2Ids.containsKey(module.moduleName)) {
        result.add(module)
      }
    }
    return result
  }

  fun computeEnabledModuleMap(disabler: Predicate<IdeaPluginDescriptorImpl>? = null): PluginSetBuilder {
    val logMessages = ArrayList<String>()

    m@ for (module in moduleGraph.nodes) {
      if (module.moduleName == null) {
        if (module.pluginId != PluginManagerCore.CORE_ID && (!module.isEnabled || (disabler != null && disabler.test(module)))) {
          continue
        }
      }
      else if (!enabledPluginIds.containsKey(module.pluginId)) {
        continue
      }

      for (ref in module.dependencies.modules) {
        if (!enabledModuleV2Ids.containsKey(ref.name)) {
          logMessages.add("Module ${module.moduleName ?: module.pluginId} is not enabled because dependency ${ref.name} is not available")
          continue@m
        }
      }
      for (ref in module.dependencies.plugins) {
        if (!enabledPluginIds.containsKey(ref.id)) {
          logMessages.add("Module ${module.moduleName ?: module.pluginId} is not enabled because dependency ${ref.id} is not available")
          continue@m
        }
      }

      if (module.moduleName == null) {
        enabledPluginIds.put(module.pluginId, module)
        for (v1Module in module.modules) {
          enabledPluginIds.put(v1Module, module)
        }
        if (module.packagePrefix != null) {
          enabledModuleV2Ids.put(module.pluginId.idString, module)
        }
      }
      else {
        enabledModuleV2Ids.put(module.moduleName, module)
      }
    }

    if (!logMessages.isEmpty()) {
      PluginManagerCore.getLogger().info(logMessages.joinToString(separator = "\n"))
    }
    return this
  }

  fun createPluginSetWithEnabledModulesMap(): PluginSet {
    return computeEnabledModuleMap().createPluginSet()
  }

  fun createPluginSet(incompletePlugins: Collection<IdeaPluginDescriptorImpl> = Collections.emptyList()): PluginSet {
    val sortedPlugins = getSortedPlugins()
    val allPlugins = LinkedHashSet<IdeaPluginDescriptorImpl>(sortedPlugins.size + incompletePlugins.size)
    allPlugins += sortedPlugins
    allPlugins += incompletePlugins

    val enabledPlugins = sortedPlugins.filterTo(ArrayList(sortedPlugins.size)) { it.isEnabled }

    val java11Shim = Java11Shim.INSTANCE
    return PluginSet(
      moduleGraph = moduleGraph,
      allPlugins = java11Shim.copyOf(allPlugins),
      enabledPlugins = java11Shim.copyOfCollection(enabledPlugins),
      enabledModuleMap = java11Shim.copyOf(enabledModuleV2Ids),
      enabledPluginAndV1ModuleMap = java11Shim.copyOf(enabledPluginIds),
      enabledModules = java11Shim.copyOfCollection(getEnabledModules()),
    )
  }

  fun checkModules(descriptor: IdeaPluginDescriptorImpl, isDebugLogEnabled: Boolean, log: Logger) {
    m@ for (item in descriptor.content.modules) {
      for (ref in item.requireDescriptor().dependencies.modules) {
        if (!enabledModuleV2Ids.containsKey(ref.name)) {
          if (isDebugLogEnabled) {
            log.info("Module ${item.name} is not enabled because dependency ${ref.name} is not available")
          }
          continue@m
        }
      }
      for (ref in item.requireDescriptor().dependencies.plugins) {
        if (!enabledPluginIds.containsKey(ref.id)) {
          if (isDebugLogEnabled) {
            log.info("Module ${item.name} is not enabled because dependency ${ref.id} is not available")
          }
          continue@m
        }
      }
      enabledModuleV2Ids.put(item.name, descriptor)
    }
  }

  // use only for init plugins
  internal fun initEnableState(
    descriptor: IdeaPluginDescriptorImpl,
    idMap: Map<PluginId, IdeaPluginDescriptorImpl>,
    disabledPlugins: Set<PluginId>,
    errors: MutableMap<PluginId, PluginLoadingError>,
  ): PluginLoadingError? {
    val isNotifyUser = !descriptor.isImplementationDetail
    for (incompatibleId in descriptor.incompatibilities) {
      if (!enabledPluginIds.containsKey(incompatibleId) || disabledPlugins.contains(incompatibleId)) {
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

    descriptor.allPluginDependencies
      .firstOrNull { it !in enabledPluginIds }
      ?.let { dependencyPluginId ->
        return idMap.get(dependencyPluginId)?.let {
          createTransitivelyDisabledError(descriptor, it, isNotifyUser)
        } ?: createCannotLoadError(descriptor, dependencyPluginId, errors, isNotifyUser)
      }

    return descriptor.moduleDependencies
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

private val IdeaPluginDescriptorImpl.allPluginDependencies
  get(): Sequence<PluginId> {
    return pluginDependencies.asSequence()
             .filterNot { it.isOptional }
             .map { it.pluginId } +
           dependencies
             .plugins.asSequence()
             .map { it.id }
  }

private val IdeaPluginDescriptorImpl.moduleDependencies
  get(): Sequence<String> {
    return dependencies
      .modules.asSequence()
      .map { it.name }
  }