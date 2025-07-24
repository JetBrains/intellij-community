// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.PluginUtils.joinedPluginIds
import com.intellij.ide.plugins.PluginUtils.parseAsPluginIdSet
import com.intellij.ide.plugins.PluginUtils.toPluginIdSet
import com.intellij.openapi.application.JetBrainsProtocolHandler
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.registry.EarlyAccessRegistryManager
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import java.io.IOException
import java.nio.file.Path
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.io.path.Path

@ApiStatus.Internal
class DisabledPluginsState internal constructor() : PluginEnabler.Headless {
  companion object {
    private val DISABLED_PLUGINS_CUSTOM_FILE_PATH = System.getProperty("disabled.plugins.file.path")

    const val DISABLED_PLUGINS_FILENAME: @NonNls String = "disabled_plugins.txt"

    @Volatile
    private var disabledPlugins: Set<PluginId>? = null
    private val ourDisabledPluginListeners = CopyOnWriteArrayList<Runnable>()

    @Volatile
    private var isDisabledStateIgnored = EarlyAccessRegistryManager.getBoolean("idea.ignore.disabled.plugins")

    private val defaultFilePath: Path
      get() {
        if (DISABLED_PLUGINS_CUSTOM_FILE_PATH != null) {
          return Path(DISABLED_PLUGINS_CUSTOM_FILE_PATH)
        }
        return PathManager.getConfigDir().resolve(DISABLED_PLUGINS_FILENAME)
      }

    // do not use class reference here
    @Suppress("SSBasedInspection")
    private val logger: Logger
      get() = Logger.getInstance("#com.intellij.ide.plugins.DisabledPluginsState")

    fun addDisablePluginListener(listener: Runnable) {
      ourDisabledPluginListeners.add(listener)
    }

    fun removeDisablePluginListener(listener: Runnable) {
      ourDisabledPluginListeners.remove(listener)
    }

    fun getRequiredPlugins(): Set<PluginId> {
      return splitByComma(JetBrainsProtocolHandler.REQUIRED_PLUGINS_KEY)
    }

    fun loadDisabledPlugins(path: Path): Set<PluginId> {
      val disabledPlugins = LinkedHashSet<PluginId>()
      val requiredPlugins = getRequiredPlugins()
      var updateFile = false
      try {
        val pluginIdsFromFile = PluginStringSetFile.readIdsSafe(path, logger)
        val suppressedPluginIds = splitByComma("idea.suppressed.plugins.id")
        val suppressedPluginsSet = getSuppressedPluginsSet()

        if (pluginIdsFromFile.isEmpty() && suppressedPluginIds.isEmpty() && suppressedPluginsSet.isEmpty()) {
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
        return disabledPlugins + suppressedPluginsSet.filter { !applicationInfo.isEssentialPlugin(it) }
      }
      finally {
        if (updateFile) {
          trySaveDisabledPlugins(disabledPlugins, false)
        }
      }
    }

    // Allows specifying named sets of disabled plugins.
    // For instance, in the case of CLion, we want to have two distinct sets of incompatible plugins:
    //  - for CLion "Nova"
    //  - for CLion "Classic"
    //
    // The difference between this and "idea.suppressed.plugins.id" is that we allow user to switch
    // between the sets, so we store the selector on the user's machine in config directory.
    // But the actual content of a set may be changed by us during the update if necessary.
    // Also, we do not want ids from the sets to be saved inside "disabled_plugins.txt",
    // because it may break IDE during the update.
    private fun getSuppressedPluginsSet(): Set<PluginId> {
      val selector = System.getProperty("idea.suppressed.plugins.set.selector") ?: return emptySet()
      return splitByComma("idea.suppressed.plugins.set.${selector}")
    }

    fun getDisabledIds(): Set<PluginId> {
      disabledPlugins?.let { return it }

      if (isDisabledStateIgnored) {
        return Collections.emptySet()
      }

      synchronized(DisabledPluginsState::class.java) {
        var result = disabledPlugins
        if (result == null) {
          @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
          result = Collections.unmodifiableSet(loadDisabledPlugins(defaultFilePath))!!
          disabledPlugins = result
        }
        return result
      }
    }

    @JvmName("setEnabledState")
    internal fun setEnabledState(descriptors: Collection<IdeaPluginDescriptor>, enabled: Boolean): Boolean {
      val pluginIds = descriptors.toPluginIdSet()
      return setEnabledState(pluginIds, enabled)
    }

    @JvmName("setEnabledStateForIds")
    internal fun setEnabledState(pluginIds: Set<PluginId>, enabled: Boolean): Boolean {
      val disabled = getDisabledIds().toMutableSet()
      val changed = if (enabled) disabled.removeAll(pluginIds) else disabled.addAll(pluginIds)
      if (changed) {
        disabledPlugins = Collections.unmodifiableSet(disabled)
      }
      val actuallyChanged = changed && saveDisabledPluginsAndInvalidate(disabled)
      val operation = if (enabled) "enable" else "disable"
      logger.info("${pluginIds.joinedPluginIds(operation)}, ${if (actuallyChanged) "applied" else " was already ${operation}d, nothing changed"}")
      return actuallyChanged
    }

    fun saveDisabledPluginsAndInvalidate(pluginIds: Set<PluginId>): Boolean {
      return trySaveDisabledPlugins(pluginIds = pluginIds, invalidate = true)
    }

    private fun trySaveDisabledPlugins(pluginIds: Set<PluginId>, invalidate: Boolean): Boolean {
      if (!PluginStringSetFile.writeIdsSafe(defaultFilePath, pluginIds, logger)) {
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
    fun saveDisabledPluginsAndInvalidate(configPath: Path, pluginIds: List<String> = emptyList()) {
      PluginStringSetFile.write(configPath.resolve(DISABLED_PLUGINS_FILENAME), pluginIds.toSet())
      invalidate()
    }

    fun invalidate() {
      disabledPlugins = null
    }

    private fun splitByComma(key: String): Set<PluginId> {
      val property = System.getProperty(key, "")
      return if (property.isEmpty()) emptySet() else property.split(',').parseAsPluginIdSet()
    }
  }

  override fun isIgnoredDisabledPlugins(): Boolean = isDisabledStateIgnored

  override fun setIgnoredDisabledPlugins(ignoredDisabledPlugins: Boolean) {
    isDisabledStateIgnored = ignoredDisabledPlugins
  }

  override fun isDisabled(pluginId: PluginId): Boolean = getDisabledIds().contains(pluginId)

  override fun enable(descriptors: Collection<IdeaPluginDescriptor>): Boolean = setEnabledState(descriptors, enabled = true)

  override fun disable(descriptors: Collection<IdeaPluginDescriptor>): Boolean = setEnabledState(descriptors, enabled = false)

  override fun disableById(pluginIds: Set<PluginId>): Boolean = setEnabledState(pluginIds, enabled = false)
}
