// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.BuildNumber
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readLines

@ApiStatus.Internal
class BundledPluginsState {

  init {
    val savedBuildNumber = PropertiesComponent.getInstance().savedBuildNumber
    val currentBuildNumber = ApplicationInfo.getInstance().build

    val shouldSave = savedBuildNumber == null
                     || savedBuildNumber < currentBuildNumber
                     || (!ApplicationManager.getApplication().isUnitTestMode && PluginManagerCore.isRunningFromSources())

    if (shouldSave) {
      val bundledPluginIds = loadedPluginIds

      ProcessIOExecutorService.INSTANCE.execute {
        runCatching {
          writePluginIdsToFile(bundledPluginIds)
        }.onFailure { e ->
          when (e) {
            is IOException -> LOG.warn("Unable to save bundled plugins list", e)
            else -> throw e
          }
        }.onSuccess {
          PropertiesComponent.getInstance().savedBuildNumber = currentBuildNumber
        }
      }
    }
  }

  companion object {

    @ApiStatus.Internal
    const val BUNDLED_PLUGINS_FILENAME = "bundled_plugins.txt"

    private val LOG = logger<BundledPluginsState>()

    val loadedPluginIds: Set<PluginId>
      @VisibleForTesting get() = PluginManagerCore.getLoadedPlugins()
        .asSequence()
        .filter { it.isBundled }
        .map { it.pluginId }
        .toSet()

    @VisibleForTesting
    fun writePluginIdsToFile(
      pluginIds: Set<PluginId>,
      configDir: Path = PathManager.getConfigDir(),
    ) {
      PluginManagerCore.writePluginIdsToFile(
        configDir.resolve(BUNDLED_PLUGINS_FILENAME),
        pluginIds,
      )
    }

    fun getBundledPlugins(configDir: Path = PathManager.getConfigDir()): Set<PluginId> {
      val file = configDir.resolve(BUNDLED_PLUGINS_FILENAME)
      if (!file.exists()
          || !Files.isRegularFile(file)) {
        return emptySet()
      }

      return try {
        file.readLines()
          .asSequence()
          .map { PluginId.getId(it) }
          .toSet()
      }
      catch (e: IOException) {
        LOG.warn("Unable to load bundled plugins list from $file", e)
        emptySet()
      }
    }
  }
}

private const val SAVED_VERSION_KEY = "bundled.plugins.list.saved.version"
var PropertiesComponent.savedBuildNumber: BuildNumber?
  @VisibleForTesting get() = getValue(SAVED_VERSION_KEY)?.let { BuildNumber.fromString(it) }
  private set(value) = setValue(SAVED_VERSION_KEY, value?.asString())
