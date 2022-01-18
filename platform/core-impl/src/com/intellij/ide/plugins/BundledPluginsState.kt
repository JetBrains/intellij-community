// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
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
import kotlin.io.path.readLines
import kotlin.io.path.writeText

@ApiStatus.Internal
class BundledPluginsState {
  init {
    if (shouldSave()) {
      ProcessIOExecutorService.INSTANCE.execute {
        val bundledIds = PluginManagerCore.getLoadedPlugins().filter { it.isBundled }
        saveBundledPluginsOrLog(bundledIds)
      }
    }
  }

  companion object {
    const val BUNDLED_PLUGINS_FILENAME: @NonNls String = "bundled_plugins.txt"
    private const val SAVED_VERSION_KEY: @NonNls String = "bundled.plugins.list.saved.version"
    private val logger = Logger.getInstance(this::class.java)

    fun shouldSave(): Boolean {
      val savedVersion = PropertiesComponent.getInstance().getValue(SAVED_VERSION_KEY)?.let { BuildNumber.fromString(it) } ?: return true
      return (!ApplicationManager.getApplication().isUnitTestMode && PluginManagerCore.isRunningFromSources()) || savedVersion < ApplicationInfo.getInstance().build
    }

    fun saveBundledPluginsOrLog(plugins: List<IdeaPluginDescriptor>) {
      val file = PathManager.getConfigDir().resolve(BUNDLED_PLUGINS_FILENAME)
      try {
        saveBundledPlugins(file, plugins)
        PropertiesComponent.getInstance().setValue(SAVED_VERSION_KEY, ApplicationInfo.getInstance().build.asString())
      }
      catch (e: IOException) {
        logger.warn("Unable to save bundled plugins list", e)
      }
    }

    @JvmStatic
    fun getBundledPlugins(configDir: Path): List<Pair<PluginId, Category>>? {
      val file = configDir.resolve(BUNDLED_PLUGINS_FILENAME)
      if (!file.exists()) {
        return null
      }
      else if (!Files.isRegularFile(file)) {
        return null
      }
      val bundledPlugins = mutableListOf<Pair<PluginId, Category>>()
      try {
        file.readLines().map {
          val splitResult = it.trim().split("|")
          if (splitResult.size != 2) {
            logger.warn("Incompatible format for bundled plugins list: $file")
            return null
          }
          val (id, category) = splitResult
          bundledPlugins.add(Pair(PluginId.getId(id), if (category == "null") null else category))
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

    private fun saveBundledPlugins(file: Path,
                                   plugins: List<IdeaPluginDescriptor>) {
      NioFiles.createDirectories(file.parent)
      file.writeText(plugins.joinToString("") { "${it.pluginId.idString}|${it.category}\n" })
    }
  }

}

typealias Category = String?