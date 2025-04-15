// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.diagnostic.LoadingState
import com.intellij.ide.plugins.marketplace.statistics.PluginManagerUsageCollector
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.JComponent

private val LOG
  get() = logger<DynamicPluginEnabler>()

fun interface PluginEnableStateChangedListener{
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

  override fun isDisabled(pluginId: PluginId): Boolean =
    PluginEnabler.HEADLESS.isDisabled(pluginId)

  override fun enable(descriptors: Collection<IdeaPluginDescriptor>): Boolean =
    enable(descriptors, project = null)

  fun enable(
    descriptors: Collection<IdeaPluginDescriptor>,
    project: Project? = null,
  ): Boolean {
    if (descriptors.any { !PluginManagerCore.isCompatible(it) }) {
      // mark plugins enabled and require restart
      PluginManagerUsageCollector.pluginsStateChanged(descriptors, enable = true, project)
      PluginEnabler.HEADLESS.enable(descriptors)

      return false
    }

    if (LoadingState.APP_STARTED.isOccurred) {
      PluginManagerUsageCollector.pluginsStateChanged(descriptors, enable = true, project)
    }

    PluginEnabler.HEADLESS.enable(descriptors)
    val installedDescriptors = findInstalledPlugins(descriptors) ?: return false
    val pluginsLoaded = DynamicPlugins.loadPlugins(installedDescriptors, project)

    for (listener in pluginEnableStateChangedListeners) {
      try {
        listener.stateChanged(descriptors, true)
      } catch (ex: Exception) {
        LOG.warn("An exception occurred while processing enablePlugins in $listener", ex)
      }
    }
    return pluginsLoaded
  }

  override fun disable(descriptors: Collection<IdeaPluginDescriptor>): Boolean =
    disable(descriptors, project = null)

  fun disable(
    descriptors: Collection<IdeaPluginDescriptor>,
    project: Project? = null,
    parentComponent: JComponent? = null,
  ): Boolean {
    PluginManagerUsageCollector.pluginsStateChanged(descriptors, enable = false, project)

    PluginEnabler.HEADLESS.disable(descriptors)
    val installedDescriptors = findInstalledPlugins(descriptors) ?: return false
    val pluginsUnloaded = DynamicPlugins.unloadPlugins(installedDescriptors, project, parentComponent)
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

private fun findInstalledPlugins(descriptors: Collection<IdeaPluginDescriptor>): List<IdeaPluginDescriptorImpl>? {
  val result = descriptors.mapNotNull {
    runCatching { findInstalledPlugin(it) }
      .getOrLogException(LOG)
  }
  return if (result.size == descriptors.size) result else null
}

private fun findInstalledPlugin(descriptor: IdeaPluginDescriptor): IdeaPluginDescriptorImpl {
  return when (descriptor) {
    is IdeaPluginDescriptorImpl -> descriptor
    is PluginNode -> {
      val pluginId = descriptor.pluginId

      PluginManagerCore.getPluginSet().findInstalledPlugin(pluginId)
      ?: throw IllegalStateException("Plugin '$pluginId' is not installed")
    }
    else -> throw IllegalArgumentException("Unknown descriptor kind: $descriptor")
  }
}