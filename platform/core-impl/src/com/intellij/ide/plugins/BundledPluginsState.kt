// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.io.NioFiles
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

@ApiStatus.Internal
class BundledPluginsState {
  init {
    if (shouldSave()) {
      val bundledIds = mutableListOf<IdeaPluginDescriptor>()
      val plugins = PluginManagerCore.getLoadedPlugins()
      for (plugin in plugins) {
        if (plugin.isBundled) {
          bundledIds.add(plugin)
        }
      }
      setBundledPlugins(bundledIds)
    }
  }

  companion object {
    const val BUNDLED_PLUGINS_FILENAME: @NonNls String = "bundled_plugins.txt"
    const val SAVED_VERSION_KEY: @NonNls String = "bundled.plugins.list.saved.version"
    private val logger = Logger.getInstance(this::class.java)

    fun getBundledIdsForOtherIde(configDir: Path): List<PluginWithCategory>? {
      return loadBundledPlugins(configDir)
    }

    fun shouldSave(): Boolean {
      val currentVersion = PropertiesComponent.getInstance().getValue(SAVED_VERSION_KEY)?.let { BuildNumber.fromString(it) } ?: return true
      return PluginManagerCore.isRunningFromSources() || currentVersion < ApplicationInfo.getInstance().build
    }

    fun setBundledPlugins(plugins: List<IdeaPluginDescriptor>): Boolean {
      return trySaveBundledPlugins(PathManager.getConfigDir().resolve(BUNDLED_PLUGINS_FILENAME), plugins)
    }

    private fun trySaveBundledPlugins(file: Path,
                                      plugins: List<IdeaPluginDescriptor>): Boolean {
      return try {
        saveBundledPlugins(file, plugins)
        PropertiesComponent.getInstance().setValue(SAVED_VERSION_KEY, ApplicationInfo.getInstance().build.asString())
        true
      }
      catch (e: IOException) {
        logger.warn("Unable to save bundled plugins list", e)
        false
      }
    }

    private fun saveBundledPlugins(file: Path,
                                   plugins: List<IdeaPluginDescriptor>) {
      NioFiles.createDirectories(file.parent)
      Files.newBufferedWriter(file).use { writer ->
        for (id in plugins) {
          writer.write("${id.pluginId.idString} | ${id.category}")
          writer.write('\n'.code)
        }
      }
    }

    private fun loadBundledPlugins(configDir: Path): List<PluginWithCategory>? {
      val file = configDir.resolve(BUNDLED_PLUGINS_FILENAME)
      if (!file.exists()) {
        return null
      }
      else if (!Files.isRegularFile(file)) {
        return null
      }
      val bundledPlugins = mutableListOf<PluginWithCategory>()
      try {
        Files.newBufferedReader(file).use { reader ->
          var line: String?
          while (reader.readLine().also { line = it } != null) {
            val splitResult = line!!.split("|")
            if (splitResult.size != 2) {
              logger.warn("Incompatible format for bundled plugins list: $file")
              return null
            }
            var (id, category) = splitResult
            id = id.trim()
            category = category.trim()
            bundledPlugins.add(PluginWithCategory(PluginId.getId(id), if (category == "null") null else category))
          }
        }
      }
      catch (e: IOException) {
        logger.warn("Unable to load bundled plugins list from $file", e)
      }
      if (bundledPlugins.isEmpty()) {
        return null
      }
      return bundledPlugins
    }
  }

  data class PluginWithCategory(val id: PluginId, val category: String?)
}