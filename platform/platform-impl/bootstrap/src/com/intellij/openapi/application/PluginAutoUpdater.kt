// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.intellij.ide.plugins.*
import com.intellij.openapi.application.PluginAutoUpdateRepository.PluginUpdateInfo
import com.intellij.openapi.application.PluginAutoUpdateRepository.clearUpdates
import com.intellij.openapi.application.PluginAutoUpdateRepository.getAutoUpdateDirPath
import com.intellij.openapi.application.PluginAutoUpdateRepository.safeConsumeUpdates
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.updateSettings.impl.PluginDownloader
import com.intellij.openapi.updateSettings.impl.findUnsatisfiedDependencies
import com.intellij.platform.diagnostic.telemetry.impl.span
import com.intellij.platform.ide.bootstrap.ZipFilePoolImpl
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.Result
import kotlin.io.path.exists

@ApiStatus.Internal
object PluginAutoUpdater {
  const val SKIP_PLUGIN_AUTO_UPDATE_PROPERTY: String = "ide.skip.plugin.auto.update"

  @Volatile
  private var pluginAutoUpdateResult: Result<PluginAutoUpdateStatistics>? = null

  fun shouldSkipAutoUpdate(): Boolean = System.getProperty(SKIP_PLUGIN_AUTO_UPDATE_PROPERTY) == "true"

  /**
   * This method is called during startup, before the plugins are loaded.
   */
  suspend fun applyPluginUpdates(logDeferred: Deferred<Logger>) {
    val updates = safeConsumeUpdates(logDeferred).filter { (_, info) ->
      runCatching {
        Path.of(info.pluginPath).exists() && getAutoUpdateDirPath().resolve(info.updateFilename).exists()
      }.getOrElse { e ->
        logDeferred.await().warn(e)
        false
      }
    }
    pluginAutoUpdateResult = runCatching {
      val updatesApplied = applyPluginUpdates(updates, logDeferred)
      PluginAutoUpdateStatistics(updatesPrepared = updates.size, pluginsUpdated = updatesApplied)
    }.apply {
      getOrLogException { e ->
        logDeferred.await().error("Error occurred during application of plugin updates", e)
      }
    }
    runCatching {
      clearUpdates()
    }.getOrLogException { e ->
      logDeferred.await().warn("Failed to clear plugin auto update directory", e)
    }
  }

  /**
   * @return number of successfully applied updates
   */
  private suspend fun applyPluginUpdates(updates: Map<PluginId, PluginUpdateInfo>, logDeferred: Deferred<Logger>): Int {
    if (updates.isEmpty()) {
      return 0
    }
    logDeferred.await().info("There are ${updates.size} prepared updates for plugins. Applying...")
    val autoupdatesDir = getAutoUpdateDirPath()

    val currentDescriptors = span("loading existing descriptors") {
      ZipFilePoolImpl().use { pool ->
        val discoveredPlugins = loadDescriptors(
          CompletableDeferred(pool),
          CompletableDeferred(PluginAutoUpdateRepository::class.java.classLoader),
        ).second
        val loadingResult = PluginLoadingResult()
        loadingResult.initAndAddAll(descriptorLoadingResult = discoveredPlugins, initContext = ProductPluginInitContext())
        loadingResult
      }
    }
    // shadowing intended
    val updates = updates.filter { (id, _) ->
      (!PluginManagerCore.isDisabled(id) && (currentDescriptors.getIdMap()[id] != null || currentDescriptors.getIncompleteIdMap()[id] != null))
        .also { pluginForUpdateExists ->
          if (!pluginForUpdateExists) logDeferred.await().warn("Update for plugin $id is declined since the plugin is not going to be loaded")
        }
    }
    val updateDescriptors = span("update descriptors loading") {
      updates.mapValues { (_, info) ->
        val updateFile = autoupdatesDir.resolve(info.updateFilename)
        async(Dispatchers.IO) {
          runCatching { loadAndInitDescriptorFromArtifact(updateFile, null) }
        }
      }.mapValues { it.value.await() }
    }.filter {
      (it.value.getOrNull() != null).also { loaded ->
        if (!loaded) logDeferred.await().warn("Update for plugin ${it.key} has failed to load", it.value.exceptionOrNull())
      }
    }.mapValues { it.value.getOrNull()!! }

    val updateCheck = determineValidUpdates(currentDescriptors, updateDescriptors)
    updateCheck.rejectedUpdates.forEach { (id, reason) ->
      logDeferred.await().warn("Update for plugin $id has been rejected: $reason")
    }
    var updatesApplied = 0
    for (id in updateCheck.updatesToApply) {
      val update = updates[id]!!
      runCatching {
        PluginInstaller.unpackPlugin(getAutoUpdateDirPath().resolve(update.updateFilename), PathManager.getPluginsDir())
      }.onFailure {
        logDeferred.await().warn("Failed to apply update for plugin $id", it)
      }.onSuccess {
        logDeferred.await().info("Plugin $id has been successfully updated: " +
                                 "version ${currentDescriptors.getIdMap()[id]?.version} -> ${updateDescriptors[id]!!.version}")
        updatesApplied++
      }
    }
    return updatesApplied
  }

  private data class UpdateCheckResult(
    val updatesToApply: Set<PluginId>,
    val rejectedUpdates: Map<PluginId, String>,
  )

  private fun determineValidUpdates(
    currentDescriptors: PluginLoadingResult,
    updates: Map<PluginId, IdeaPluginDescriptorImpl>,
  ): UpdateCheckResult {
    val updatesToApply = mutableSetOf<PluginId>()
    val rejectedUpdates = mutableMapOf<PluginId, String>()
    // checks mostly duplicate what is written in com.intellij.ide.plugins.PluginInstaller.installFromDisk. FIXME, I guess
    val enabledPluginsAndModulesIds: Set<String> = currentDescriptors.getIdMap().flatMap { entry ->
      val desc = entry.value
      listOf(desc.pluginId.idString) + desc.pluginAliases.map { it.idString } + desc.content.modules.map { it.name }
    }.toSet()
    for ((id, updateDesc) in updates) {
      val existingDesc = currentDescriptors.getIdMap()[id] ?: currentDescriptors.getIncompleteIdMap()[id]
      if (existingDesc == null) {
        rejectedUpdates[id] = "plugin $id is not installed"
        continue
      }
      // no third-party plugin check, settings are not available at this point; that check must be done when downloading the updates
      if (PluginManagerCore.isIncompatible(updateDesc)) {
        rejectedUpdates[id] = "plugin $id of version ${updateDesc.version} is not compatible with current IDE build"
        continue
      }
      if (isBrokenPlugin(updateDesc)) {
        rejectedUpdates[id] = "plugin $id of version ${updateDesc.version} is known to be broken"
        continue
      }
      if (ApplicationInfoImpl.getShadowInstance().isEssentialPlugin(id)) {
        rejectedUpdates[id] = "plugin $id is part of the IDE distribution and cannot be updated without IDE update"
        continue
      }
      if (PluginDownloader.compareVersionsSkipBrokenAndIncompatible(updateDesc.version, existingDesc) <= 0) {
        rejectedUpdates[id] = "plugin $id has same or newer version installed (${existingDesc.version} vs update version ${updateDesc.version})"
        continue
      }
      // I guess a more robust way to check which updates should be applied and which not is the following.
      // Greedily try to apply all the updates and then exclude those which turn out to be incompatible (on the module graph level).
      // Repeat until the set of updates doesn't produce incompatibilities.
      // We have to keep in mind that the set of dependencies may change arbitrarily with the plugin update.
      //
      // But for now we just check that each of the updates is compatible. Formally speaking, we don't fully check this condition and
      // the behavior may actually differ from the honest check. To implement it better, the plugin loading implementation should be a little
      // bit more formalized and a bit more flexible to be reused here (TODO).
      val unmetDependencies = findUnsatisfiedDependencies(
        updateDesc.dependencies,
        enabledPluginsAndModulesIds
      )
      if (unmetDependencies.isNotEmpty()) {
        rejectedUpdates[id] = "plugin $id of version ${updateDesc.version} has unsatisfied dependencies " +
                              "(plugin ids): ${unmetDependencies.joinToString(", ") { it.pluginId.idString }}"
        continue
      }
      // TODO check signature ? com.intellij.ide.plugins.marketplace.PluginSignatureChecker; probably also should be done after download
      updatesToApply.add(id)
    }
    return UpdateCheckResult(updatesToApply, rejectedUpdates)
  }

  /**
   * @return not null if plugin auto update was triggered
   */
  fun getPluginAutoUpdateResult(): Result<PluginAutoUpdateStatistics>? = pluginAutoUpdateResult

  data class PluginAutoUpdateStatistics(
    val updatesPrepared: Int,
    val pluginsUpdated: Int,
  )
}