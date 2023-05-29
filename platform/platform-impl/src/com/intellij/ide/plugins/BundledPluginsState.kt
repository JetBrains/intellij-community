// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("CompanionObjectInExtension")

package com.intellij.ide.plugins

import com.intellij.ide.ApplicationInitializedListener
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.BuildNumber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readLines

@ApiStatus.Internal
class BundledPluginsState : ApplicationInitializedListener {
  companion object {
    private const val SAVED_VERSION_KEY = "bundled.plugins.list.saved.version"

    var PropertiesComponent.savedBuildNumber: BuildNumber?
      @VisibleForTesting get() = getValue(SAVED_VERSION_KEY)?.let { BuildNumber.fromString(it) }
      private set(value) = setValue(SAVED_VERSION_KEY, value?.asString())

    @ApiStatus.Internal
    const val BUNDLED_PLUGINS_FILENAME: String = "bundled_plugins.txt"

    private val LOG: Logger
      get() = logger<BundledPluginsState>()

    val loadedPlugins: Set<IdeaPluginDescriptor>
      @VisibleForTesting get() = PluginManagerCore.getLoadedPlugins().filterTo(HashSet()) { it.isBundled }

    @VisibleForTesting
    fun writePluginIdsToFile(pluginIds: Set<IdeaPluginDescriptor>, configDir: Path = PathManager.getConfigDir()) {
      PluginManagerCore.writePluginIdsToFile(
        configDir.resolve(BUNDLED_PLUGINS_FILENAME),
        pluginIds.map { "${it.pluginId.idString}|${it.category}\n" },
      )
    }

    fun readPluginIdsFromFile(path: Path = PathManager.getConfigDir()): Set<Pair<PluginId, Category>> {
      val file = path.resolve(BUNDLED_PLUGINS_FILENAME)
      if (!file.exists()) {
        return emptySet()
      }
      else if (!Files.isRegularFile(file)) {
        return emptySet()
      }

      val bundledPlugins = mutableSetOf<Pair<PluginId, Category>>()
      try {
        file.readLines().map(String::trim)
          .filter { !it.isEmpty() }.map {
            val splitResult = it.split("|")
            val id = splitResult.first()
            val category = splitResult.getOrNull(1)
            bundledPlugins.add(Pair(PluginId.getId(id), if (category == "null") null else category))
          }
      }
      catch (e: IOException) {
        LOG.warn("Unable to load bundled plugins list from $file", e)
      }
      return bundledPlugins
    }
  }

  override suspend fun execute(asyncScope: CoroutineScope) {
    asyncScope.launch {
      val app = ApplicationManager.getApplication()
      val savedBuildNumber = app.serviceAsync<PropertiesComponent>().savedBuildNumber
      val currentBuildNumber = ApplicationInfo.getInstance().build

      val shouldSave = savedBuildNumber == null
                       || savedBuildNumber < currentBuildNumber
                       || (!app.isUnitTestMode && PluginManagerCore.isRunningFromSources())

      if (!shouldSave) {
        return@launch
      }

      val bundledPluginIds = loadedPlugins

      withContext(Dispatchers.IO) {
        try {
          writePluginIdsToFile(bundledPluginIds)

          PropertiesComponent.getInstance().savedBuildNumber = currentBuildNumber
        }
        catch (e: IOException) {
          LOG.warn("Unable to save bundled plugins list", e)
        }
      }
    }
  }
}

typealias Category = String?