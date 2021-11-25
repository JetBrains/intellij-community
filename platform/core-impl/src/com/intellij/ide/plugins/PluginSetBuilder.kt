// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("ReplaceGetOrSet", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceNegatedIsEmptyWithIsNotEmpty")

package com.intellij.ide.plugins

import com.intellij.core.CoreBundle
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.lang.Java11Shim
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey
import java.util.*
import java.util.function.Predicate
import java.util.function.Supplier

@ApiStatus.Internal
class PluginSetBuilder(val unsortedPlugins: List<IdeaPluginDescriptorImpl>) {
  private val _moduleGraph = createModuleGraph(unsortedPlugins)
  private val builder = _moduleGraph.builder()
  val moduleGraph: SortedModuleGraph = _moduleGraph.sorted(builder)

  private val enabledPluginIds = HashMap<PluginId, IdeaPluginDescriptorImpl>(unsortedPlugins.size)
  private val enabledModuleV2Ids = HashMap<String, IdeaPluginDescriptorImpl>(unsortedPlugins.size * 2)

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

  fun createPluginSet(incompletePlugins: Collection<IdeaPluginDescriptorImpl> = Collections.emptyList()): PluginSet {
    val java11Shim = Java11Shim.INSTANCE

    val allPlugins: List<IdeaPluginDescriptorImpl>
    val sortedPlugins = getSortedPlugins()
    if (incompletePlugins.isEmpty()) {
      allPlugins = ContainerUtil.immutableList(*sortedPlugins)
    }
    else {
      val result = ArrayList<IdeaPluginDescriptorImpl>(sortedPlugins.size + incompletePlugins.size)
      result.addAll(sortedPlugins)
      result.addAll(incompletePlugins)
      allPlugins = java11Shim.copyOfCollection(result)
    }

    val enabledPlugins = java11Shim.copyOfCollection(sortedPlugins.filterTo(ArrayList(sortedPlugins.size)) { it.isEnabled })

    return PluginSet(
      moduleGraph = moduleGraph,
      allPlugins = allPlugins,
      enabledPlugins = enabledPlugins,
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
  internal fun initEnableState(descriptor: IdeaPluginDescriptorImpl,
                               idMap: Map<PluginId, IdeaPluginDescriptorImpl>,
                               disabledRequired: MutableSet<IdeaPluginDescriptorImpl>,
                               disabledPlugins: Set<PluginId>,
                               errors: MutableMap<PluginId, PluginLoadingError>): Boolean {
    val notifyUser = !descriptor.isImplementationDetail
    for (incompatibleId in descriptor.incompatibilities) {
      if (!enabledPluginIds.containsKey(incompatibleId) || disabledPlugins.contains(incompatibleId)) {
        continue
      }

      val presentableName = incompatibleId.idString
      errors.put(descriptor.pluginId, PluginLoadingError(
        plugin = descriptor,
        detailedMessageSupplier = message("plugin.loading.error.long.ide.contains.conflicting.module", descriptor.name, presentableName),
        shortMessageSupplier = message("plugin.loading.error.short.ide.contains.conflicting.module", presentableName),
        isNotifyUser = notifyUser,
      ))
      return false
    }

    for (dependency in descriptor.pluginDependencies) {
      val depId = dependency.pluginId
      if (dependency.isOptional || enabledPluginIds.containsKey(depId)) {
        continue
      }

      val dep = idMap.get(depId)
      if (dep != null && disabledPlugins.contains(depId)) {
        // broken/incompatible plugins can be updated, add them anyway
        disabledRequired.add(dep)
      }
      addCannotLoadError(descriptor, errors, notifyUser, depId, dep)
      return false
    }

    for (item in descriptor.dependencies.plugins) {
      if (enabledPluginIds.containsKey(item.id)) {
        continue
      }

      val dep = idMap.get(item.id)
      if (dep != null && disabledPlugins.contains(item.id)) {
        // broken/incompatible plugins can be updated, add them anyway
        disabledRequired.add(dep)
      }
      addCannotLoadError(descriptor, errors, notifyUser, item.id, dep)
      return false
    }

    for (item in descriptor.dependencies.modules) {
      if (enabledModuleV2Ids.containsKey(item.name)) {
        continue
      }

      errors.put(descriptor.pluginId, PluginLoadingError(
        plugin = descriptor,
        detailedMessageSupplier = message("plugin.loading.error.long.depends.on.not.installed.plugin", descriptor.name, item.name),
        shortMessageSupplier = message("plugin.loading.error.short.depends.on.not.installed.plugin", item.name),
        isNotifyUser = notifyUser,
      ))
      return false
    }
    return true
  }
}

private fun addCannotLoadError(descriptor: IdeaPluginDescriptorImpl,
                               errors: MutableMap<PluginId, PluginLoadingError>,
                               notifyUser: Boolean,
                               depId: PluginId,
                               dep: IdeaPluginDescriptor?) {
  val depName = dep?.name
  if (depName == null) {
    val depPresentableId = depId.idString
    if (errors.containsKey(depId)) {
      val depError = errors.get(depId)!!
      val depNameFromError = depError.plugin.name
      errors.put(descriptor.pluginId, PluginLoadingError(
        plugin = descriptor,
        detailedMessageSupplier = message("plugin.loading.error.long.depends.on.failed.to.load.plugin",
                                          descriptor.name, depNameFromError ?: depPresentableId),
        shortMessageSupplier = message("plugin.loading.error.short.depends.on.failed.to.load.plugin", depPresentableId),
        isNotifyUser = notifyUser,
        disabledDependency = null
      ))
    }
    else {
      errors.put(descriptor.pluginId, PluginLoadingError(
        plugin = descriptor,
        detailedMessageSupplier = message("plugin.loading.error.long.depends.on.not.installed.plugin", descriptor.name, depPresentableId),
        shortMessageSupplier = message("plugin.loading.error.short.depends.on.not.installed.plugin", depPresentableId),
        isNotifyUser = notifyUser, disabledDependency = null
      ))
    }
  }
  else {
    errors.put(descriptor.pluginId, PluginLoadingError(
      plugin = descriptor,
      detailedMessageSupplier = message("plugin.loading.error.long.depends.on.disabled.plugin", descriptor.name, depName),
      shortMessageSupplier = message("plugin.loading.error.short.depends.on.disabled.plugin", depName),
      isNotifyUser = notifyUser,
      disabledDependency = dep.pluginId
    ))
  }
}

private fun message(key: @PropertyKey(resourceBundle = CoreBundle.BUNDLE) String, vararg params: Any): @Nls Supplier<String> {
  return Supplier { CoreBundle.message(key, *params) }
}