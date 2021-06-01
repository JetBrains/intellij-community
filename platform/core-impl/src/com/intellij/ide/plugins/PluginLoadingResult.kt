// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.core.CoreBundle
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.BuildNumber
import com.intellij.util.PlatformUtils
import com.intellij.util.text.VersionComparatorUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Supplier

// https://www.jetbrains.org/intellij/sdk/docs/basics/getting_started/plugin_compatibility.html
// If a plugin does not include any module dependency tags in its plugin.xml,
// it's assumed to be a legacy plugin and is loaded only in IntelliJ IDEA.
@ApiStatus.Internal
class PluginLoadingResult(private val brokenPluginVersions: Map<PluginId, Set<String?>>,
                          @JvmField val productBuildNumber: Supplier<BuildNumber>,
                          private val checkModuleDependencies: Boolean = !PlatformUtils.isIntelliJ()) {
  @JvmField val incompletePlugins = ConcurrentHashMap<PluginId, IdeaPluginDescriptorImpl>()
  private val plugins = HashMap<PluginId, IdeaPluginDescriptorImpl>()

  // only read is concurrent, write from the only thread
  @JvmField val idMap = ConcurrentHashMap<PluginId, IdeaPluginDescriptorImpl>()
  @JvmField var duplicateModuleMap: MutableMap<PluginId, MutableList<IdeaPluginDescriptorImpl>>? = null
  private val pluginErrors = ConcurrentHashMap<PluginId, PluginLoadingError>()
  private val globalErrors = Collections.synchronizedList(ArrayList<Supplier<String>>())
  @JvmField val shadowedBundledIds = HashSet<PluginId>()

  // result, after calling finishLoading
  private var enabledPlugins: List<IdeaPluginDescriptorImpl>? = null

  @get:TestOnly
  val hasPluginErrors: Boolean
    get() = !pluginErrors.isEmpty()

  fun getEnabledPlugins(): List<IdeaPluginDescriptorImpl> = enabledPlugins!!

  fun enabledPluginCount() = plugins.size

  fun finishLoading() {
    val enabledPlugins = plugins.values.toTypedArray()
    plugins.clear()
    Arrays.sort(enabledPlugins, Comparator.comparing { it.pluginId })
    @Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
    this.enabledPlugins = Arrays.asList(*enabledPlugins)
  }

  fun isBroken(id: PluginId): Boolean {
    val set = brokenPluginVersions.get(id) ?: return false
    val descriptor = idMap.get(id)
    return descriptor != null && set.contains(descriptor.version)
  }

  internal fun getPluginErrors(): Map<PluginId, PluginLoadingError> = Collections.unmodifiableMap(pluginErrors)

  fun getGlobalErrors(): List<Supplier<String>> {
    synchronized(globalErrors) {
      return ArrayList(globalErrors)
    }
  }

  internal fun addIncompletePlugin(plugin: IdeaPluginDescriptorImpl, error: PluginLoadingError?) {
    if (!idMap.containsKey(plugin.pluginId)) {
      val existingIncompletePlugin = incompletePlugins[plugin.pluginId]
      if (existingIncompletePlugin == null || VersionComparatorUtil.compare(plugin.version, existingIncompletePlugin.version) > 0) {
        incompletePlugins.put(plugin.pluginId, plugin)
      }
    }
    if (error != null) {
      pluginErrors.put(plugin.pluginId, error)
    }
  }

  internal fun reportIncompatiblePlugin(plugin: IdeaPluginDescriptorImpl, error: PluginLoadingError) {
    // do not report if some compatible plugin were already added
    // no race condition here: plugins from classpath are loaded before and not in parallel to loading from plugin dir
    if (!idMap.containsKey(plugin.pluginId)) {
      pluginErrors.put(plugin.pluginId, error)
    }
  }

  fun reportCannotLoad(file: Path, e: Throwable?) {
    PluginManagerCore.getLogger().warn("Cannot load $file", e)
    globalErrors.add(Supplier {
      CoreBundle.message("plugin.loading.error.text.file.contains.invalid.plugin.descriptor", pluginPathToUserString(file))
    })
  }

  fun add(descriptor: IdeaPluginDescriptorImpl, overrideUseIfCompatible: Boolean): Boolean {
    val pluginId = descriptor.pluginId
    if (descriptor.isIncomplete) {
      return true
    }

    if (!descriptor.isBundled) {
      if (checkModuleDependencies && !PluginManagerCore.hasModuleDependencies(descriptor)) {
        val error = PluginLoadingError(plugin = descriptor,
                                       detailedMessageSupplier = { CoreBundle.message("plugin.loading.error.long.compatible.with.intellij.idea.only", descriptor.name) },
                                       shortMessageSupplier = { CoreBundle.message("plugin.loading.error.short.compatible.with.intellij.idea.only") },
                                       isNotifyUser = true)
        addIncompletePlugin(descriptor, error)
        return false
      }
    }

    // remove any error that occurred for plugin with the same id
    pluginErrors.remove(pluginId)
    incompletePlugins.remove(pluginId)
    val prevDescriptor = plugins.put(pluginId, descriptor)
    if (prevDescriptor == null) {
      idMap.put(pluginId, descriptor)
      for (module in descriptor.modules) {
        checkAndAdd(descriptor, module)
      }
      return true
    }

    if (prevDescriptor.isBundled || descriptor.isBundled) {
      shadowedBundledIds.add(pluginId)
    }

    if (isCompatible(descriptor) &&
        (overrideUseIfCompatible || VersionComparatorUtil.compare(descriptor.version, prevDescriptor.version) > 0)) {
      PluginManagerCore.getLogger().info("${descriptor.pluginPath} overrides ${prevDescriptor.pluginPath}")
      idMap.put(pluginId, descriptor)
      return true
    }
    else {
      plugins.put(pluginId, prevDescriptor)
      return false
    }
  }

  private fun isCompatible(descriptor: IdeaPluginDescriptorImpl): Boolean {
    return PluginManagerCore.checkBuildNumberCompatibility(descriptor, productBuildNumber.get()) == null
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