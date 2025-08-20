// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")
package com.intellij.ide.plugins

import com.intellij.core.CoreBundle
import com.intellij.idea.AppMode
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.BuildNumber
import com.intellij.util.text.VersionComparatorUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting

@ApiStatus.Internal
class PluginLoadingResult {
  private val incompletePlugins = HashMap<PluginId, PluginMainDescriptor>()

  @JvmField
  @ApiStatus.Internal
  val enabledPluginsById: HashMap<PluginId, PluginMainDescriptor> = HashMap()

  fun getPluginsToAttemptLoading(): Set<PluginMainDescriptor> = enabledPluginsById.values.toSet()

  private val idMap = HashMap<PluginId, PluginMainDescriptor>() // FIXME this does not account plugin aliases in content modules
  @JvmField var duplicateModuleMap: MutableMap<PluginId, MutableList<PluginMainDescriptor>>? = null
  // the order of errors matters
  private val pluginErrors = LinkedHashMap<PluginId, PluginNonLoadReason>()

  @VisibleForTesting
  @JvmField val shadowedBundledIds: MutableSet<PluginId> = HashSet()

  @get:TestOnly
  val hasPluginErrors: Boolean
    get() = !pluginErrors.isEmpty()

  @get:TestOnly
  val enabledPlugins: List<PluginMainDescriptor>
    get() = enabledPluginsById.entries.sortedBy { it.key }.map { it.value }

  internal fun copyPluginErrors(): MutableMap<PluginId, PluginNonLoadReason> = LinkedHashMap(pluginErrors)

  fun getIncompleteIdMap(): Map<PluginId, PluginMainDescriptor> = incompletePlugins

  fun getIdMap(): Map<PluginId, PluginMainDescriptor> = idMap

  private fun addIncompletePlugin(plugin: PluginMainDescriptor, error: PluginNonLoadReason?) {
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

  fun initAndAddAll(
    descriptorLoadingResult: PluginDescriptorLoadingResult,
    initContext: PluginInitializationContext,
  ) {
    for (pluginList in descriptorLoadingResult.discoveredPlugins) {
      for (descriptor in pluginList.plugins) {
        // plugins added via property shouldn't be overridden to avoid plugin root detection issues when running external plugin tests
        initAndAdd(descriptor = descriptor, overrideUseIfCompatible = pluginList.source is PluginsSourceContext.SystemPropertyProvided, initContext = initContext)
      }
    }
  }

  private fun initAndAdd(descriptor: PluginMainDescriptor, overrideUseIfCompatible: Boolean, initContext: PluginInitializationContext) {
    initContext.pluginsPerProjectConfig?.let { conf ->
      if (conf.isMainProcess && descriptor.pluginId !in initContext.essentialPlugins) {
        return
      }
    }
    descriptor.initialize(initContext)?.let { error ->
      descriptor.isMarkedForLoading = false
      addIncompletePlugin(plugin = descriptor, error = error.takeIf { it !is PluginIsMarkedDisabled })
      return
    }

    if (initContext.requirePlatformAliasDependencyForLegacyPlugins && PluginCompatibilityUtils.isLegacyPluginWithoutPlatformAliasDependencies(descriptor)) {
      addIncompletePlugin(descriptor, PluginIsCompatibleOnlyWithIntelliJIDEA(descriptor))
      return
    }

    // remove any error that occurred for plugin with the same `id`
    val pluginId = descriptor.pluginId
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

    if (PluginManagerCore.checkBuildNumberCompatibility(descriptor, initContext.productBuildNumber) == null &&
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

  private fun PluginMainDescriptor.initialize(context: PluginInitializationContext): PluginNonLoadReason? {
    content.modules.forEach { it.requireDescriptor() }
    if (content.modules.size > 1) {
      val duplicates = HashSet<PluginModuleId>()
      for (item in content.modules) {
        if (!duplicates.add(item.moduleId)) {
          return PluginHasDuplicateContentModuleDeclaration(this, item.moduleId)
        }
      }
    }
    if (context.isPluginDisabled(pluginId)) {
      return PluginIsMarkedDisabled(this)
    }
    checkCompatibility(context::productBuildNumber, context::isPluginBroken)?.let {
      return it
    }
    for (dependency in pluginDependencies) { // FIXME: likely we actually have to recursively traverse these after they are resolved
      if (context.isPluginDisabled(dependency.pluginId) && !dependency.isOptional) {
        return PluginDependencyIsDisabled(this, dependency.pluginId, false)
      }
    }
    for (pluginDependency in moduleDependencies.plugins) {
      if (context.isPluginDisabled(pluginDependency)) {
        return PluginDependencyIsDisabled(this, pluginDependency, false)
      }
    }
    return null
  }

  private fun PluginMainDescriptor.checkCompatibility(getBuildNumber: () -> BuildNumber, isPluginBroken: (PluginId, version: String?) -> Boolean): PluginNonLoadReason? {
    if (isPluginWhichDependsOnKotlinPluginAndItsIncompatibleWithIt(this)) {
      // disable plugins which are incompatible with the Kotlin Plugin K1/K2 Modes KTIJ-24797, KTIJ-30474
      val mode = if (isKotlinPluginK1Mode()) CoreBundle.message("plugin.loading.error.k1.mode") else CoreBundle.message("plugin.loading.error.k2.mode")
      return PluginIsIncompatibleWithKotlinMode(this, mode)
    }
    if (isBundled) {
      return null
    }
    if (AppMode.isDisableNonBundledPlugins()) {
      return NonBundledPluginsAreExplicitlyDisabled(this)
    }
    PluginManagerCore.checkBuildNumberCompatibility(this, getBuildNumber())?.let {
      return it
    }
    // "Show broken plugins in Settings | Plugins so that users can uninstall them and resolve 'Plugin Error' (IDEA-232675)"
    if (isPluginBroken(pluginId, version)) {
      return PluginIsMarkedBroken(this)
    }
    return null
  }

  private fun checkAndAdd(descriptor: PluginMainDescriptor, id: PluginId) {
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
    val list = ArrayList<PluginMainDescriptor>(2)
    list.add(existingDescriptor)
    list.add(descriptor)
    duplicateModuleMap!!.put(id, list)
  }
}