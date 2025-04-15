// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")
package com.intellij.ide.plugins

import com.intellij.core.CoreBundle
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.BuildNumber
import com.intellij.util.PlatformUtils
import com.intellij.util.text.VersionComparatorUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting

// https://plugins.jetbrains.com/docs/intellij/plugin-compatibility.html
// If a plugin does not include any module dependency tags in its plugin.xml,
// it's assumed to be a legacy plugin and is loaded only in IntelliJ IDEA.
@ApiStatus.Internal
class PluginLoadingResult(private val checkModuleDependencies: Boolean = !PlatformUtils.isIntelliJ()) {
  private val incompletePlugins = HashMap<PluginId, IdeaPluginDescriptorImpl>()

  @JvmField
  @ApiStatus.Internal
  val enabledPluginsById: HashMap<PluginId, IdeaPluginDescriptorImpl> = HashMap()

  private val idMap = HashMap<PluginId, IdeaPluginDescriptorImpl>()
  @JvmField var duplicateModuleMap: MutableMap<PluginId, MutableList<IdeaPluginDescriptorImpl>>? = null
  // the order of errors matters
  private val pluginErrors = LinkedHashMap<PluginId, PluginLoadingError>()

  @VisibleForTesting
  @JvmField val shadowedBundledIds: MutableSet<PluginId> = HashSet()

  @get:TestOnly
  val hasPluginErrors: Boolean
    get() = !pluginErrors.isEmpty()

  @get:TestOnly
  val enabledPlugins: List<IdeaPluginDescriptorImpl>
    get() = enabledPluginsById.entries.sortedBy { it.key }.map { it.value }

  internal fun copyPluginErrors(): MutableMap<PluginId, PluginLoadingError> = LinkedHashMap(pluginErrors)

  fun getIncompleteIdMap(): Map<PluginId, IdeaPluginDescriptorImpl> = incompletePlugins

  fun getIdMap(): Map<PluginId, IdeaPluginDescriptorImpl> = idMap

  private fun addIncompletePlugin(plugin: IdeaPluginDescriptorImpl, error: PluginLoadingError?) {
    // do not report if some compatible plugin were already added
    // no race condition here: plugins from classpath are loaded before and not in parallel to loading from plugin dir
    if (idMap.containsKey(plugin.pluginId)) {
      return
    }

    val existingIncompletePlugin = incompletePlugins.putIfAbsent(plugin.pluginId, plugin)
    if (existingIncompletePlugin != null && VersionComparatorUtil.compare(plugin.version, existingIncompletePlugin.version) > 0) {
      incompletePlugins.put(plugin.pluginId, plugin)
      if (error != null) {
        // force put
        pluginErrors.put(plugin.pluginId, error)
      }
    }
    else if (error != null) {
      pluginErrors.putIfAbsent(plugin.pluginId, error)
    }
  }

  /**
   * @see [com.intellij.openapi.project.ex.ProjectManagerEx]
   */
  fun addAll(descriptors: Sequence<IdeaPluginDescriptorImpl>, overrideUseIfCompatible: Boolean, productBuildNumber: BuildNumber) {
    for (descriptor in descriptors) {
      add(descriptor = descriptor, overrideUseIfCompatible = overrideUseIfCompatible, productBuildNumber = productBuildNumber)
    }
  }

  @TestOnly
  fun addAll(descriptors: List<IdeaPluginDescriptorImpl>) {
    val productBuildNumber = BuildNumber.fromString("2042.42")!!
    for (descriptor in descriptors) {
      add(descriptor = descriptor, overrideUseIfCompatible = false, productBuildNumber = productBuildNumber)
    }
  }

  private fun add(descriptor: IdeaPluginDescriptorImpl, overrideUseIfCompatible: Boolean, productBuildNumber: BuildNumber) {
    val pluginId = descriptor.pluginId
    descriptor.isIncomplete?.let { error ->
      addIncompletePlugin(plugin = descriptor, error = error.takeIf { !it.isDisabledError })
      return
    }

    if (checkModuleDependencies && isCheckingForImplicitDependencyNeeded(descriptor)) {
      addIncompletePlugin(descriptor, PluginLoadingError(
        plugin = descriptor,
        detailedMessageSupplier = { CoreBundle.message("plugin.loading.error.long.compatible.with.intellij.idea.only", descriptor.name) },
        shortMessageSupplier = { CoreBundle.message("plugin.loading.error.short.compatible.with.intellij.idea.only") },
        isNotifyUser = true
      ))
      return
    }

    // remove any error that occurred for plugin with the same `id`
    pluginErrors.remove(pluginId)
    incompletePlugins.remove(pluginId)
    val prevDescriptor = enabledPluginsById.put(pluginId, descriptor)
    if (prevDescriptor == null) {
      idMap.put(pluginId, descriptor)
      for (pluginAlias in descriptor.pluginAliases) {
        checkAndAdd(descriptor, pluginAlias)
      }
      return
    }

    if (prevDescriptor.isBundled || descriptor.isBundled) {
      shadowedBundledIds.add(pluginId)
    }

    if (PluginManagerCore.checkBuildNumberCompatibility(descriptor, productBuildNumber) == null &&
        (overrideUseIfCompatible || VersionComparatorUtil.compare(descriptor.version, prevDescriptor.version) > 0)) {
      PluginManagerCore.logger.info("$descriptor overrides $prevDescriptor")
      idMap.put(pluginId, descriptor)
      return
    }
    else {
      enabledPluginsById.put(pluginId, prevDescriptor)
      return
    }
  }

  private fun checkAndAdd(descriptor: IdeaPluginDescriptorImpl, id: PluginId) {
    duplicateModuleMap?.get(id)?.let { duplicates ->
      duplicates.add(descriptor)
      return
    }

    val existingDescriptor = idMap.put(id, descriptor) ?: return

    // if duplicated, both are removed
    idMap.remove(id)
    if (duplicateModuleMap == null) {
      duplicateModuleMap = LinkedHashMap()
    }
    val list = ArrayList<IdeaPluginDescriptorImpl>(2)
    list.add(existingDescriptor)
    list.add(descriptor)
    duplicateModuleMap!!.put(id, list)
  }
}

// todo merge into PluginSetState?
@ApiStatus.Internal
data class PluginManagerState internal constructor(
  @JvmField val pluginSet: PluginSet,
  @JvmField val pluginIdsToDisable: Set<PluginId>,
  @JvmField val pluginIdsToEnable: Set<PluginId>,
)

// skip our plugins as expected to be up to date whether bundled or not
internal fun isCheckingForImplicitDependencyNeeded(descriptor: IdeaPluginDescriptorImpl): Boolean {
  return !descriptor.isBundled &&
         descriptor.packagePrefix == null &&
         !descriptor.isImplementationDetail &&
         descriptor.content.modules.isEmpty() &&
         descriptor.moduleDependencies.modules.isEmpty() &&
         descriptor.moduleDependencies.plugins.isEmpty() &&
         descriptor.pluginId != PluginManagerCore.CORE_ID &&
         descriptor.pluginId != PluginManagerCore.JAVA_PLUGIN_ID &&
         !hasModuleDependencies(descriptor)
}

private fun hasModuleDependencies(descriptor: IdeaPluginDescriptorImpl): Boolean {
  for (dependency in descriptor.dependencies) {
    val dependencyPluginId = dependency.pluginId
    if (PluginManagerCore.JAVA_PLUGIN_ID == dependencyPluginId ||
        PluginManagerCore.JAVA_MODULE_ID == dependencyPluginId ||
        PluginManagerCore.isModuleDependency(dependencyPluginId)) {
      return true
    }
  }
  return false
}