// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.io.IOException
import java.nio.file.Path

@ApiStatus.Experimental
class EnabledOnDemandPluginsState : PluginEnabler {

  companion object {

    private const val ENABLED_PLUGINS_FILENAME: @NonNls String = "enabled_on_demand_plugins.txt"

    private var enabledPluginIds_: MutableSet<PluginId>? = null

    private val defaultFilePath: Path
      get() = PathManager.getConfigDir().resolve(ENABLED_PLUGINS_FILENAME)

    private val logger
      get() = Logger.getInstance(EnabledOnDemandPluginsState::class.java)

    @JvmStatic
    fun getInstance(): EnabledOnDemandPluginsState? =
      if (IdeaPluginDescriptorImpl.isOnDemandEnabled)
        ApplicationManager.getApplication().getService(EnabledOnDemandPluginsState::class.java)
      else
        null

    @JvmStatic
    val enabledPluginIds: Set<PluginId>
      get() {
        enabledPluginIds_?.let {
          return it
        }

        synchronized(EnabledOnDemandPluginsState::class.java) {
          var result = enabledPluginIds_
          if (result == null) {
            result = LinkedHashSet(if (IdeaPluginDescriptorImpl.isOnDemandEnabled) readEnabledPlugins() else emptySet())
            enabledPluginIds_ = result
          }
          return result
        }
      }

    @JvmStatic
    fun isEnabled(pluginId: PluginId): Boolean = enabledPluginIds.contains(pluginId)

    @JvmStatic
    fun setEnabledState(descriptors: Collection<IdeaPluginDescriptor>, enabled: Boolean): Boolean {
      if (!IdeaPluginDescriptorImpl.isOnDemandEnabled) {
        return false
      }

      val pluginIds = descriptors.filter { it.isOnDemand }
        .toPluginIdSet()
      logger.info(pluginIds.joinedPluginIds("load on demand"))

      val enabledPluginIds = enabledPluginIds as MutableSet
      val changed = if (enabled) enabledPluginIds.addAll(pluginIds) else enabledPluginIds.removeAll(pluginIds)
      return changed && writeEnabledPlugins(enabledPluginIds)
    }

    private fun readEnabledPlugins(): Set<PluginId> {
      return try {
        DisabledPluginsState.readPluginIdsFromFile(defaultFilePath)
      }
      catch (e: IOException) {
        logger.info("Unable to load enabled plugins list", e)
        emptySet()
      }
    }

    private fun writeEnabledPlugins(pluginIds: Set<PluginId>): Boolean {
      try {
        PluginManagerCore.writePluginIdsToFile(defaultFilePath, pluginIds)
        return true
      }
      catch (e: IOException) {
        logger.warn("Unable to save enabled plugins list", e)
        return false
      }
    }
  }

  init {
    if (IdeaPluginDescriptorImpl.isOnDemandEnabled) {
      logger.info(enabledPluginIds.joinedPluginIds("load"))
    }
  }

  override fun isDisabled(pluginId: PluginId): Boolean = !isEnabled(pluginId)

  override fun enable(descriptors: Collection<IdeaPluginDescriptor>): Boolean = setEnabledState(descriptors, enabled = true)

  override fun disable(descriptors: Collection<IdeaPluginDescriptor>): Boolean = setEnabledState(descriptors, enabled = false)
}
