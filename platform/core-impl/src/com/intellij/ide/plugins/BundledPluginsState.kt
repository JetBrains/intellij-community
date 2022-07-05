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
import java.nio.file.Path

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

    fun readPluginIdsFromFile(configDir: Path = PathManager.getConfigDir()): Set<PluginId> {
      return try {
        PluginManagerCore.readPluginIdsFromFile(configDir.resolve(BUNDLED_PLUGINS_FILENAME))
      }
      catch (e: IOException) {
        LOG.warn("Unable to load bundled plugins list", e)
        emptySet()
      }
    }
  }
}

private const val SAVED_VERSION_KEY = "bundled.plugins.list.saved.version"
var PropertiesComponent.savedBuildNumber: BuildNumber?
  @VisibleForTesting get() = getValue(SAVED_VERSION_KEY)?.let { BuildNumber.fromString(it) }
  private set(value) = setValue(SAVED_VERSION_KEY, value?.asString())
