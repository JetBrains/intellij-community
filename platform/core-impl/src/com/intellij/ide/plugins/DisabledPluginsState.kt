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
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.stream.Collectors

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

    @JvmStatic
    fun addDisablePluginListener(listener: Runnable) {
      ourDisabledPluginListeners.add(listener)
    }

    @JvmStatic
    fun removeDisablePluginListener(listener: Runnable) {
      ourDisabledPluginListeners.remove(listener)
    }

    internal fun readPluginIdsFromFile(path: Path): Set<PluginId> {
      try {
        Files.lines(path).use { lines ->
          return lines
            .map(String::trim)
            .filter { !it.isEmpty() }
            .map(PluginId::getId)
            .collect(Collectors.toSet())
        }
      }
      catch (ignored: NoSuchFileException) {
        return Collections.emptySet()
      }
    }

    @JvmStatic
    fun loadDisabledPlugins(): Set<PluginId> {
      val disabledPlugins = LinkedHashSet<PluginId>()
      val path = defaultFilePath
      val requiredPlugins = splitByComma(JetBrainsProtocolHandler.REQUIRED_PLUGINS_KEY)
      var updateFile = false
      try {
        val pluginIdsFromFile = readPluginIdsFromFile(path)
        val suppressedPluginIds = splitByComma("idea.suppressed.plugins.id")

        if (pluginIdsFromFile.isEmpty() && suppressedPluginIds.isEmpty()) {
          return Collections.emptySet()
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
      catch (e: IOException) {
        logger.info("Unable to load disabled plugins list from $path", e)
        return Collections.emptySet()
      }
      finally {
        if (updateFile) {
          trySaveDisabledPlugins(disabledPlugins, false)
        }
      }
    }

    @JvmStatic
    fun disabledPlugins(): Set<PluginId> = getDisabledIds()

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

    internal fun setEnabledState(plugins: Set<PluginId>, enabled: Boolean): Boolean {
      val disabled = getDisabledIds().toMutableSet()
      val changed = if (enabled) disabled.removeAll(plugins) else disabled.addAll(plugins)
      if (changed) {
        disabledPlugins = Collections.unmodifiableSet(disabled)
      }
      logger.info(joinedPluginIds(plugins, enabled))
      return changed && saveDisabledPluginsAndInvalidate(disabled)
    }

    @JvmStatic
    fun saveDisabledPluginsAndInvalidate(pluginIds: Set<PluginId>): Boolean {
      return trySaveDisabledPlugins(pluginIds = pluginIds, invalidate = true)
    }

    private fun trySaveDisabledPlugins(pluginIds: Set<PluginId>, invalidate: Boolean): Boolean {
      try {
        PluginManagerCore.writePluginIdsToFile(defaultFilePath, pluginIds)
      }
      catch (e: IOException) {
        logger.warn("Unable to save disabled plugins list", e)
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

    private val defaultFilePath: Path
      get() = PathManager.getConfigDir().resolve(DISABLED_PLUGINS_FILENAME)

    // do not use class reference here
    @Suppress("SSBasedInspection")
    private val logger: Logger
      // do not use class reference here
      get() = Logger.getInstance("#com.intellij.ide.plugins.DisabledPluginsState")

    fun invalidate() {
      disabledPlugins = null
    }

    private fun splitByComma(key: String): Set<PluginId> {
      val property = System.getProperty(key, "")
      if (property.isEmpty()) {
        return emptySet()
      }

      val result = HashSet<PluginId>()
      for (s in property.split(',')) {
        result.add(PluginId.getId(s.trim().takeIf(String::isNotEmpty) ?: continue))
      }
      return result
    }

    private fun joinedPluginIds(pluginIds: Collection<PluginId>, enabled: Boolean): String {
      val buffer = StringBuilder("Plugins to ")
        .append(if (enabled) "enable" else "disable")
        .append(": [")
      val iterator = pluginIds.iterator()
      while (iterator.hasNext()) {
        buffer.append(iterator.next().idString)
        if (iterator.hasNext()) {
          buffer.append(", ")
        }
      }
      return buffer.append(']').toString()
    }
  }

  override fun isIgnoredDisabledPlugins() = isDisabledStateIgnored

  override fun setIgnoredDisabledPlugins(ignoredDisabledPlugins: Boolean) {
    isDisabledStateIgnored = ignoredDisabledPlugins
  }

  override fun isDisabled(pluginId: PluginId) = getDisabledIds().contains(pluginId)

  override fun enable(descriptors: Collection<IdeaPluginDescriptor>) = enableById(descriptors.toPluginIdSet())

  override fun disable(descriptors: Collection<IdeaPluginDescriptor>) = disableById(descriptors.toPluginIdSet())

  override fun enableById(pluginIds: Set<PluginId>) = setEnabledState(plugins = pluginIds, enabled = true)

  override fun disableById(pluginIds: Set<PluginId>) = setEnabledState(plugins = pluginIds, enabled = false)
}