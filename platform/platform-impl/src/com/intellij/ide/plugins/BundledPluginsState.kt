// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("CompanionObjectInExtension")

package com.intellij.ide.plugins

import com.intellij.ide.ApplicationInitializedListener
import com.intellij.ide.plugins.BundledPluginsState.Companion.savedBuildNumber
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.BuildNumber
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes

private const val SAVED_VERSION_KEY = "bundled.plugins.list.saved.version"

private val LOG: Logger
  get() = logger<BundledPluginsState>()

@ApiStatus.Internal
const val BUNDLED_PLUGINS_FILENAME: String = "bundled_plugins.txt"

@ApiStatus.Internal
class BundledPluginsState : ApplicationInitializedListener {
  companion object {
    var PropertiesComponent.savedBuildNumber: BuildNumber?
      @VisibleForTesting get() = getValue(SAVED_VERSION_KEY)?.let { BuildNumber.fromString(it) }
      internal set(value) = setValue(SAVED_VERSION_KEY, value?.asString())

    val loadedPlugins: Set<IdeaPluginDescriptor>
      @VisibleForTesting get() = PluginManagerCore.loadedPlugins.filterTo(HashSet()) { it.isBundled }
  }

  init {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(asyncScope: CoroutineScope) {
    asyncScope.launch {
      // postpone avoiding getting PropertiesComponent and writing to disk too early
      delay(1.minutes)

      saveBundledPluginsState()
    }
  }
}

@VisibleForTesting
suspend fun saveBundledPluginsState() {
  val savedBuildNumber = serviceAsync<PropertiesComponent>().savedBuildNumber
  val currentBuildNumber = ApplicationInfo.getInstance().build

  val shouldSave = savedBuildNumber == null
                   || savedBuildNumber < currentBuildNumber
                   || (!ApplicationManager.getApplication().isUnitTestMode && PluginManagerCore.isRunningFromSources())

  if (!shouldSave) {
    return
  }

  val bundledPluginIds = BundledPluginsState.loadedPlugins
  withContext(Dispatchers.IO) {
    try {
      writePluginIdsToFile(bundledPluginIds)
      serviceAsync<PropertiesComponent>().savedBuildNumber = currentBuildNumber
    }
    catch (e: IOException) {
      LOG.warn("Unable to save bundled plugins list", e)
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
    LOG.warn("Unable to load bundled plugins list from $file", e)
  }
  return bundledPlugins
}

typealias Category = String?