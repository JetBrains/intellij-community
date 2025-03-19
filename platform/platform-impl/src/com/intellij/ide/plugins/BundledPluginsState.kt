// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

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
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

@ApiStatus.Internal
const val BUNDLED_PLUGINS_FILENAME: String = "bundled_plugins.txt"

private fun getSavedBuildNumber(settingsController: SettingsController,
                                        settingDescriptor: SettingDescriptor<String>): BuildNumber? {
  return settingsController.getItem(settingDescriptor)?.let { BuildNumber.fromString(it) }
}

internal fun setSavedBuildNumber(value: BuildNumber?,
                                 settingsController: SettingsController,
                                 settingDescriptor: SettingDescriptor<String>) {
  settingsController.setItem(settingDescriptor, value?.asString())
}

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
      writePluginIdsToFile(bundledPluginIds)
      setSavedBuildNumber(currentBuildNumber, settingsController, settingDescriptor)
    }
    catch (e: IOException) {
      PluginManagerCore.logger.warn("Unable to save bundled plugins list", e)
    }
  }
}

@VisibleForTesting
fun writePluginIdsToFile(pluginIds: Set<IdeaPluginDescriptor>, configDir: Path = PathManager.getConfigDir()) {
  PluginManagerCore.writePluginIdsToFile(
    path = configDir.resolve(BUNDLED_PLUGINS_FILENAME),
    pluginIds = pluginIds.map { "${it.pluginId.idString}|${it.category}\n" },
  )
}

fun readPluginIdsFromFile(configDir: Path = PathManager.getConfigDir()): Set<Pair<PluginId, Category>> {
  val file = configDir.resolve(BUNDLED_PLUGINS_FILENAME)
  if (!Files.exists(file)) {
    return emptySet()
  }
  else if (!Files.isRegularFile(file)) {
    return emptySet()
  }

  val bundledPlugins = mutableSetOf<Pair<PluginId, Category>>()
  try {
    Files.readAllLines(file).map(String::trim)
      .filter { !it.isEmpty() }.map {
        val splitResult = it.split('|')
        val id = splitResult.first()
        val category = splitResult.getOrNull(1)
        bundledPlugins.add(Pair(PluginId.getId(id), if (category == "null") null else category))
      }
  }
  catch (e: IOException) {
    PluginManagerCore.logger.warn("Unable to load bundled plugins list from $file", e)
  }
  return bundledPlugins
}

typealias Category = String?