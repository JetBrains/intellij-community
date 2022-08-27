// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.nio.file.Path

@ApiStatus.Experimental
class EnabledOnDemandPluginsState : PluginEnabler {

  companion object {

    private const val ENABLED_PLUGINS_FILENAME: @NonNls String = "enabled_on_demand_plugins.txt"

    private var enabledPluginIds_: MutableSet<PluginId>? = null

    private val defaultFilePath: Path
      get() = PathManager.getConfigDir().resolve(ENABLED_PLUGINS_FILENAME)

    private val LOG get() = logger<EnabledOnDemandPluginsState>()

    @JvmStatic
    fun getInstance(): EnabledOnDemandPluginsState = ApplicationManager.getApplication().service()

    @JvmStatic
    val enabledPluginIds: Set<PluginId>
      get() {
        enabledPluginIds_?.let {
          return it
        }

        synchronized(EnabledOnDemandPluginsState::class.java) {
          var result = enabledPluginIds_
          if (result == null) {
            result = if (IdeaPluginDescriptorImpl.isOnDemandEnabled)
              LinkedHashSet(PluginManagerCore.tryReadPluginIdsFromFile(defaultFilePath, LOG))
            else
              mutableSetOf()
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
      LOG.info(pluginIds.joinedPluginIds("load on demand"))

      val enabledPluginIds = enabledPluginIds as MutableSet
      return (if (enabled) enabledPluginIds.addAll(pluginIds) else enabledPluginIds.removeAll(pluginIds))
             && PluginManagerCore.tryWritePluginIdsToFile(defaultFilePath, pluginIds, LOG)
    }
  }

  init {
    if (IdeaPluginDescriptorImpl.isOnDemandEnabled) {
      LOG.info(enabledPluginIds.joinedPluginIds("load"))
    }
  }

  override fun isDisabled(pluginId: PluginId): Boolean = !isEnabled(pluginId)

  override fun enable(descriptors: Collection<IdeaPluginDescriptor>): Boolean = setEnabledState(descriptors, enabled = true)

  override fun disable(descriptors: Collection<IdeaPluginDescriptor>): Boolean = setEnabledState(descriptors, enabled = false)
}
