// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.application.JetBrainsProtocolHandler
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import java.io.IOException
import java.nio.file.Path
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

@ApiStatus.Internal
class DisabledPluginsState internal constructor() : PluginEnabler.Headless {

  companion object {

    const val DISABLED_PLUGINS_FILENAME: @NonNls String = "disabled_plugins.txt"
    private val IGNORE_DISABLED_PLUGINS = java.lang.Boolean.getBoolean("idea.ignore.disabled.plugins")

    @Volatile
    private var disabledPlugins: Set<PluginId>? = null
    private val ourDisabledPluginListeners = CopyOnWriteArrayList<Runnable>()

    @Volatile
    private var isDisabledStateIgnored = IGNORE_DISABLED_PLUGINS

    private val defaultFilePath: Path
      get() = PathManager.getConfigDir().resolve(DISABLED_PLUGINS_FILENAME)

    // do not use class reference here
    @Suppress("SSBasedInspection")
    private val logger: Logger
      get() = Logger.getInstance("#com.intellij.ide.plugins.DisabledPluginsState")

    @JvmStatic
    fun addDisablePluginListener(listener: Runnable) {
      ourDisabledPluginListeners.add(listener)
    }

    @JvmStatic
    fun removeDisablePluginListener(listener: Runnable) {
      ourDisabledPluginListeners.remove(listener)
    }

    @JvmStatic
    fun loadDisabledPlugins(): Set<PluginId> {
      val disabledPlugins = LinkedHashSet<PluginId>()
      val path = defaultFilePath
      val requiredPlugins = splitByComma(JetBrainsProtocolHandler.REQUIRED_PLUGINS_KEY)
      var updateFile = false
      try {
        val pluginIdsFromFile = PluginManagerCore.tryReadPluginIdsFromFile(path, logger)
        val suppressedPluginIds = splitByComma("idea.suppressed.plugins.id")

        if (pluginIdsFromFile.isEmpty() && suppressedPluginIds.isEmpty()) {
          return emptySet()
        }

        // ApplicationInfoImpl maybe loaded in another thread - get it after readPluginIdsFromFile
        val applicationInfo = ApplicationInfoImpl.getShadowInstance()
        for (id in pluginIdsFromFile) {
          if (!requiredPlugins.contains(id) && !applicationInfo.isEssentialPlugin(id)) {
            disabledPlugins.add(id)
          }
          else {
            updateFile = true
          }
        }
        for (suppressedPluginId in suppressedPluginIds) {
          if (!applicationInfo.isEssentialPlugin(suppressedPluginId) && disabledPlugins.add(suppressedPluginId)) {
            updateFile = true
          }
        }
        return disabledPlugins
      }
      finally {
        if (updateFile) {
          trySaveDisabledPlugins(disabledPlugins, false)
        }
      }
    }

    @JvmStatic
    fun getDisabledIds(): Set<PluginId> {
      disabledPlugins?.let { return it }

      if (isDisabledStateIgnored) {
        return Collections.emptySet()
      }

      synchronized(DisabledPluginsState::class.java) {
        var result = disabledPlugins
        if (result == null) {
          @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
          result = Collections.unmodifiableSet(loadDisabledPlugins())!!
          disabledPlugins = result
        }
        return result
      }
    }

    @JvmName("setEnabledState")
    internal fun setEnabledState(descriptors: Collection<IdeaPluginDescriptor>, enabled: Boolean): Boolean {
      val pluginIds = descriptors.toPluginIdSet()

      val disabled = getDisabledIds().toMutableSet()
      val changed = if (enabled) disabled.removeAll(pluginIds) else disabled.addAll(pluginIds)
      if (changed) {
        disabledPlugins = Collections.unmodifiableSet(disabled)
      }
      logger.info(pluginIds.joinedPluginIds(if (enabled) "enable" else "disable"))

      return (changed && saveDisabledPluginsAndInvalidate(disabled))
        .or(EnabledOnDemandPluginsState.setEnabledState(descriptors, enabled))
    }

    @JvmStatic
    fun saveDisabledPluginsAndInvalidate(pluginIds: Set<PluginId>): Boolean {
      return trySaveDisabledPlugins(pluginIds = pluginIds, invalidate = true)
    }

    private fun trySaveDisabledPlugins(pluginIds: Set<PluginId>, invalidate: Boolean): Boolean {
      if (!PluginManagerCore.tryWritePluginIdsToFile(defaultFilePath, pluginIds, logger)) {
        return false
      }

      if (invalidate) {
        invalidate()
      }
      for (listener in ourDisabledPluginListeners) {
        listener.run()
      }
      return true
    }

    @TestOnly
    @Throws(IOException::class)
    fun saveDisabledPluginsAndInvalidate(configPath: Path, vararg pluginIds: String?) {
      PluginManagerCore.writePluginIdsToFile(configPath.resolve(DISABLED_PLUGINS_FILENAME), pluginIds.asList())
      invalidate()
    }

    fun invalidate() {
      disabledPlugins = null
    }

    private fun splitByComma(key: String): Set<PluginId> {
      val property = System.getProperty(key, "")
      return if (property.isEmpty())
        emptySet()
      else
        property.split(',').toPluginIds()
    }
  }

  override fun isIgnoredDisabledPlugins() = isDisabledStateIgnored

  override fun setIgnoredDisabledPlugins(ignoredDisabledPlugins: Boolean) {
    isDisabledStateIgnored = ignoredDisabledPlugins
  }

  override fun isDisabled(pluginId: PluginId) = getDisabledIds().contains(pluginId)

  override fun enable(descriptors: Collection<IdeaPluginDescriptor>): Boolean = setEnabledState(descriptors, enabled = true)

  override fun disable(descriptors: Collection<IdeaPluginDescriptor>): Boolean = setEnabledState(descriptors, enabled = false)
}