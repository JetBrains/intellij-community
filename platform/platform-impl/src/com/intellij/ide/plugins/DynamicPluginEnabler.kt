// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.diagnostic.LoadingState
import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.marketplace.statistics.PluginManagerUsageCollector
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

private val LOG = logger<DynamicPluginEnabler>()

fun interface PluginEnableStateChangedListener {
  fun stateChanged(pluginDescriptors: Collection<IdeaPluginDescriptor>, enable: Boolean)
}

@ApiStatus.Internal
class DynamicPluginEnabler : PluginEnabler {
  companion object {
    private val pluginEnableStateChangedListeners = CopyOnWriteArrayList<PluginEnableStateChangedListener>()

    @JvmStatic
    fun addPluginStateChangedListener(listener: PluginEnableStateChangedListener) {
      pluginEnableStateChangedListeners.add(listener)
    }

    @JvmStatic
    fun removePluginStateChangedListener(listener: PluginEnableStateChangedListener) {
      pluginEnableStateChangedListeners.remove(listener)
    }
  }

  override fun isDisabled(pluginId: PluginId): Boolean = PluginEnabler.HEADLESS.isDisabled(pluginId)

  @Deprecated("use suspend overload instead")
  override fun enable(descriptors: Collection<IdeaPluginDescriptor>): Boolean = enable(descriptors, project = null)

  @Deprecated("use suspend overload instead")
  fun enable(descriptors: Collection<IdeaPluginDescriptor>, project: Project? = null): Boolean {
    return enable(descriptors = descriptors, progressTitle = null, project = project)
  }

  /**
   * @return true if the change was applied dynamically, false if restart is needed
   */
  suspend fun enable(
    pluginIds: Collection<PluginId>,
    progressTitle: @Nls String? = null,
    project: Project? = null,
  ): Boolean = withContext(Dispatchers.Default) {
    val disabledStateChanged = PluginEnabler.HEADLESS.enableById(pluginIds.toSet())
    val plugins = findInstalledPluginsById(pluginIds) ?: return@withContext false

    if (LoadingState.APP_STARTED.isOccurred) {
      PluginManagerUsageCollector.pluginsStateChanged(plugins, enable = true, project)
    }
    val pluginsLoaded: Boolean
    if (!disabledStateChanged && plugins.all { PluginManagerCore.isLoaded(it) }) {
      // nothing to do
      pluginsLoaded = true
    }
    else {
      pluginsLoaded = DynamicPlugins.reconfigure(
        project = project,
        addNewCustomPlugins = emptyList(),
        forceRemovePlugins = emptyList(),
        extraStateValidator = DynamicPlugins.expectPluginsState(
          expectToLoad = plugins.map { it.pluginId }
        ),
        targetProductMode = null,
        progressTitle = progressTitle ?: DynamicPlugins.defaultLoadingTitle(plugins),
      )
    }

    for (listener in pluginEnableStateChangedListeners) { // FIXME tracks enable/disable state change, not dynamic load, but called only after reconfiguration
      try {
        listener.stateChanged(plugins, true)
      }
      catch (ex: Exception) {
        LOG.warn("An exception occurred while processing enablePlugins in $listener", ex)
      }
    }
    return@withContext pluginsLoaded
  }

  @Deprecated("use suspend overload instead")
  fun enable(
    descriptors: Collection<IdeaPluginDescriptor>,
    progressTitle: @Nls String?,
    project: Project? = null,
  ): Boolean {
    val incompatibleDescriptor = descriptors.firstOrNull { !PluginManagerCore.isCompatible(it) }
    if (incompatibleDescriptor != null) {
      LOG.info("Cannot enable plugin ${incompatibleDescriptor.pluginId} (${incompatibleDescriptor.version}): the plugin is incompatible.")
      // mark plugins enabled and require restart
      PluginManagerUsageCollector.pluginsStateChanged(descriptors, enable = true, project)
      PluginEnabler.HEADLESS.enable(descriptors)
      return false
    }

    if (LoadingState.APP_STARTED.isOccurred) {
      PluginManagerUsageCollector.pluginsStateChanged(descriptors, enable = true, project)
    }

    val disabledStateChanged = PluginEnabler.HEADLESS.enable(descriptors)
    val installedDescriptors = findInstalledPlugins(descriptors) ?: return false

    val pluginsLoaded: Boolean
    if (!disabledStateChanged && installedDescriptors.all { PluginManagerCore.isLoaded(it) }) {
      // nothing to do
      pluginsLoaded = true
    }
    else {
      val loaded = AtomicBoolean(false)
      runInEdt {
        loaded.set(DynamicPlugins.loadPlugins(installedDescriptors, project, progressTitle))
      }
      pluginsLoaded = loaded.get()
    }

    for (listener in pluginEnableStateChangedListeners) {
      try {
        listener.stateChanged(descriptors, true)
      } catch (ex: Exception) {
        LOG.warn("An exception occurred while processing enablePlugins in $listener", ex)
      }
    }
    return pluginsLoaded
  }

  @Deprecated("use suspend overload instead")
  override fun disable(descriptors: Collection<IdeaPluginDescriptor>): Boolean = disable(descriptors, project = null)

  suspend fun disable(pluginIds: Collection<PluginId>, project: Project? = null): Boolean = withContext(Dispatchers.Default) {
    PluginEnabler.HEADLESS.disableById(pluginIds.toSet())
    val plugins = findInstalledPluginsById(pluginIds) ?: return@withContext false

    PluginManagerUsageCollector.pluginsStateChanged(plugins, enable = false, project)

    val pluginsUnloaded = DynamicPlugins.reconfigure(
      project = project,
      addNewCustomPlugins = emptyList(),
      forceRemovePlugins = emptyList(),
      extraStateValidator = DynamicPlugins.expectPluginsState(
        expectNotToLoad = plugins.map { it.pluginId }
      ),
      targetProductMode = null,
      progressTitle = IdeBundle.message("modal.progress.title.unloading.plugins"),
    )
    for (listener in pluginEnableStateChangedListeners) {
      try {
        listener.stateChanged(plugins, false)
      } catch (ex: Exception) {
        LOG.warn("An exception occurred while processing disablePlugins in $listener", ex)
      }
    }
    return@withContext pluginsUnloaded
  }

  @Deprecated("use suspend overload instead")
  fun disable(
    descriptors: Collection<IdeaPluginDescriptor>,
    project: Project? = null,
  ): Boolean {
    PluginManagerUsageCollector.pluginsStateChanged(descriptors, enable = false, project)

    PluginEnabler.HEADLESS.disable(descriptors)
    val installedDescriptors = findInstalledPlugins(descriptors) ?: return false
    val pluginsUnloaded = DynamicPlugins.unloadPlugins(installedDescriptors, project)
    for (listener in pluginEnableStateChangedListeners) {
      try {
        listener.stateChanged(descriptors, false)
      } catch (ex: Exception) {
        LOG.warn("An exception occurred while processing disablePlugins in $listener", ex)
      }
    }
    return pluginsUnloaded
  }
}

private fun findInstalledPluginsById(pluginIds: Collection<PluginId>): List<PluginMainDescriptor>? {
  val pluginSet = PluginManagerCore.getPluginSet()
  return pluginIds.map { pluginId ->
    pluginSet.findInstalledPlugin(pluginId) ?: run {
      LOG.warn("Plugin $pluginId is not installed")
      return null
    }
  }.distinct()
}

private fun findInstalledPlugins(descriptors: Collection<IdeaPluginDescriptor>): List<PluginMainDescriptor>? {
  val result = descriptors.mapNotNull {
    runCatching { findInstalledPlugin(it) }
      .getOrLogException(LOG)
  }
  if (result.size != descriptors.size) {
    return null
  }
  return result.distinct() // drop duplicates just in case
}

private fun findInstalledPlugin(descriptor: IdeaPluginDescriptor): PluginMainDescriptor {
  return when (descriptor) {
    is IdeaPluginDescriptorImpl -> descriptor.getMainDescriptor()
    is PluginNode -> {
      val pluginId = descriptor.pluginId
      PluginManagerCore.getPluginSet().findInstalledPlugin(pluginId)
      ?: throw IllegalStateException("Plugin '$pluginId' is not installed")
    }
    else -> throw IllegalArgumentException("Unknown descriptor kind: $descriptor")
  }
}
