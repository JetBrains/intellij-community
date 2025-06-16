// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.PluginUtils.joinedPluginIds
import com.intellij.ide.plugins.PluginUtils.toPluginIdSet
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls
import java.nio.file.Path

@Internal
class ExpiredPluginsState : PluginEnabler {

  companion object {

    const val EXPIRED_PLUGINS_FILENAME: @NonNls String = "expired_plugins.txt"

    private var expiredPluginIds_: MutableSet<PluginId>? = null

    private val defaultFilePath: Path
      get() = PathManager.getConfigDir().resolve(EXPIRED_PLUGINS_FILENAME)

    private val logger
      get() = Logger.getInstance(ExpiredPluginsState::class.java)

    @JvmStatic
    fun getInstance(): ExpiredPluginsState =
      ApplicationManager.getApplication().getService(ExpiredPluginsState::class.java)

    @JvmStatic
    val expiredPluginIds: Set<PluginId>
      get() {
        expiredPluginIds_?.let {
          return it
        }

        synchronized(ExpiredPluginsState::class.java) {
          var result = expiredPluginIds_
          if (result == null) {
            result = LinkedHashSet(PluginStringSetFile.readIdsSafe(defaultFilePath, logger))
            expiredPluginIds_ = result
          }
          return result
        }
      }

    @JvmStatic
    fun isExpired(pluginId: PluginId): Boolean = expiredPluginIds.contains(pluginId)

    @JvmStatic
    fun setExpiredState(pluginIds: Set<PluginId>, expired: Boolean): Boolean {
      logger.info(pluginIds.joinedPluginIds("expire"))

      val expiredPluginIds = expiredPluginIds as MutableSet
      return (if (expired) expiredPluginIds.addAll(pluginIds) else expiredPluginIds.removeAll(pluginIds))
             && PluginStringSetFile.writeIdsSafe(defaultFilePath, expiredPluginIds, logger)
    }
  }

  init {
    logger.info(expiredPluginIds.joinedPluginIds("skip"))
  }

  override fun isDisabled(pluginId: PluginId): Boolean = isExpired(pluginId)

  override fun enableById(pluginIds: Set<PluginId>): Boolean = setExpiredState(pluginIds, expired = true)

  override fun disableById(pluginIds: Set<PluginId>): Boolean = setExpiredState(pluginIds, expired = false)

  override fun enable(descriptors: Collection<IdeaPluginDescriptor>): Boolean = enableById(descriptors.toPluginIdSet())

  override fun disable(descriptors: Collection<IdeaPluginDescriptor>): Boolean = disableById(descriptors.toPluginIdSet())
}
