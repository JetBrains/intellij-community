// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")
package com.intellij.ide.plugins

import com.intellij.core.CoreBundle
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.BuildNumber
import com.intellij.util.PlatformUtils
import com.intellij.util.text.VersionComparatorUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.util.*
import kotlin.io.path.name

// https://www.jetbrains.org/intellij/sdk/docs/basics/getting_started/plugin_compatibility.html
// If a plugin does not include any module dependency tags in its plugin.xml,
// it's assumed to be a legacy plugin and is loaded only in IntelliJ IDEA.
@ApiStatus.Internal
class PluginLoadingResult(private val checkModuleDependencies: Boolean = !PlatformUtils.isIntelliJ()) {
  private val incompletePlugins = HashMap<PluginId, IdeaPluginDescriptorImpl>()

  @JvmField val enabledPluginsById = HashMap<PluginId, IdeaPluginDescriptorImpl>()

  private val idMap = HashMap<PluginId, IdeaPluginDescriptorImpl>()
  @JvmField var duplicateModuleMap: MutableMap<PluginId, MutableList<IdeaPluginDescriptorImpl>>? = null
  private val pluginErrors = HashMap<PluginId, PluginLoadingError>()

  @VisibleForTesting
  @JvmField val shadowedBundledIds: MutableSet<PluginId> = Collections.newSetFromMap(HashMap())

  @get:TestOnly
  val hasPluginErrors: Boolean
    get() = !pluginErrors.isEmpty()

  @get:TestOnly
  val enabledPlugins: List<IdeaPluginDescriptorImpl>
    get() = enabledPluginsById.entries.sortedBy { it.key }.map { it.value }

  internal fun copyPluginErrors(): MutableMap<PluginId, PluginLoadingError> = HashMap(pluginErrors)

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
  fun addAll(descriptors: Iterable<IdeaPluginDescriptorImpl?>, overrideUseIfCompatible: Boolean, productBuildNumber: BuildNumber) {
    val isMainProcess = java.lang.Boolean.getBoolean("ide.per.project.instance")
                        && !PathManager.getPluginsDir().name.startsWith("perProject_")

    val applicationInfoEx = ApplicationInfoImpl.getShadowInstance()
    for (descriptor in descriptors) {
      if (descriptor != null
          && (!isMainProcess || applicationInfoEx.isEssentialPlugin(descriptor.pluginId))) {
        add(descriptor, overrideUseIfCompatible, productBuildNumber)
      }
    }
  }

  private fun add(descriptor: IdeaPluginDescriptorImpl, overrideUseIfCompatible: Boolean, productBuildNumber: BuildNumber) {
    val pluginId = descriptor.pluginId
    descriptor.isIncomplete?.let { error ->
      addIncompletePlugin(descriptor, error.takeIf { !it.isDisabledError })
      return
    }

    if (checkModuleDependencies && !descriptor.isBundled && descriptor.packagePrefix == null && !hasModuleDependencies(descriptor)) {
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
      for (module in descriptor.modules) {
        checkAndAdd(descriptor, module)
      }
      return
    }

    if (prevDescriptor.isBundled || descriptor.isBundled) {
      shadowedBundledIds.add(pluginId)
    }

    if (PluginManagerCore.checkBuildNumberCompatibility(descriptor, productBuildNumber) == null &&
        (overrideUseIfCompatible || VersionComparatorUtil.compare(descriptor.version, prevDescriptor.version) > 0)) {
      PluginManagerCore.getLogger().info("$descriptor overrides $prevDescriptor")
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

internal fun hasModuleDependencies(descriptor: IdeaPluginDescriptorImpl): Boolean {
  for (dependency in descriptor.pluginDependencies) {
    val dependencyPluginId = dependency.pluginId
    if (PluginManagerCore.JAVA_PLUGIN_ID == dependencyPluginId ||
        PluginManagerCore.JAVA_MODULE_ID == dependencyPluginId ||
        PluginManagerCore.isModuleDependency(dependencyPluginId)) {
      return true
    }
  }
  return false
}