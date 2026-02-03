// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.BuildNumber
import com.intellij.platform.settings.CacheTag
import com.intellij.platform.settings.SettingDescriptor
import com.intellij.platform.settings.SettingsController
import com.intellij.platform.settings.settingDescriptorFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.nio.file.Path

@Internal
suspend fun saveBundledPluginsState() {
  val settingsController = serviceAsync<SettingsController>()
  val settingDescriptor = settingDescriptorFactory(PluginManagerCore.CORE_ID).settingDescriptor("bundled.plugins.list.saved.version") {
    tags = listOf(CacheTag)
  }

  val savedBuildNumber = getSavedBuildNumber(settingsController, settingDescriptor)
  val currentBuildNumber = ApplicationInfo.getInstance().build

  val shouldSave = savedBuildNumber == null ||
                   savedBuildNumber < currentBuildNumber ||
                   (!ApplicationManager.getApplication().isUnitTestMode && PluginManagerCore.isRunningFromSources())
  if (!shouldSave) {
    return
  }

  val bundledPluginIds = PluginManagerCore.loadedPlugins.filterTo(HashSet()) { it.isBundled }
  withContext(Dispatchers.IO) {
    try {
      writePluginIdsToFile(bundledPluginIds, PathManager.getConfigDir())
      settingsController.setItem(settingDescriptor, currentBuildNumber.asString())
    }
    catch (e: IOException) {
      PluginManagerCore.logger.warn("Unable to save bundled plugins list", e)
    }
  }
}

@Internal
fun readPluginIdsFromFile(configDir: Path): Set<BundledPlugin> {
  val path = configDir.resolve(BUNDLED_PLUGINS_FILENAME)
  return PluginStringSetFile.readSafe(path, PluginManagerCore.logger)
    .mapTo(LinkedHashSet()) { s ->
      val splitResult = s.split('|')
      val id = splitResult.first()
      BundledPlugin(id = PluginId.getId(id), category = splitResult.getOrNull(1)?.takeIf { it != "null" })
    }
}

@Internal
const val BUNDLED_PLUGINS_FILENAME: String = "bundled_plugins.txt"

@VisibleForTesting
@Internal
fun writePluginIdsToFile(pluginIds: Set<IdeaPluginDescriptor>, configDir: Path) {
  writePluginStringSet(
    path = configDir.resolve(BUNDLED_PLUGINS_FILENAME),
    strings = pluginIds.mapTo(HashSet()) { "${it.pluginId.idString}|${it.category}" },
  )
}

@Internal
data class BundledPlugin(@JvmField val id: PluginId, @JvmField val category: String?)

private fun getSavedBuildNumber(
  settingsController: SettingsController,
  settingDescriptor: SettingDescriptor<String>,
): BuildNumber? {
  return settingsController.getItem(settingDescriptor)?.let { BuildNumber.fromString(it) }
}

