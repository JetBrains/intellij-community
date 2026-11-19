// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.ide

import com.intellij.ide.IdeBundle
import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.newui.UiPluginManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.JBProtocolCommand
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.updateSettings.impl.InternalPluginResults
import com.intellij.openapi.updateSettings.impl.PluginUpdateDialog
import com.intellij.openapi.updateSettings.impl.UpdateCheckerFacade
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.installAndEnable
import com.intellij.openapi.util.NlsContexts.DialogMessage
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.ui.AppIcon
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handles `jetbrains://tool/plugin/install?id=<pluginId>[&ids=<id1>,<id2>,…]` URLs.
 *
 * The dialogs shown by [installAndEnable] and [PluginUpdateDialog] require user consent.
 * OS protocol handlers provide no verifiable origin, so this command uses no host allowlist.
 */
internal class InstallPluginJbProtocolCommand : JBProtocolCommand("plugin") {
  override suspend fun execute(target: String?, parameters: Map<String, String>, fragment: String?): @DialogMessage String? {
    if (target != "install") {
      return IdeBundle.message("jb.protocol.unknown.target", target)
    }
    val ids = collectPluginIds(parameters)
    if (ids.isEmpty()) {
      return IdeBundle.message("jb.protocol.parameter.missing", "id")
    }

    val installedIds = ids.filter { PluginManagerCore.isPluginInstalled(it) }.toSet()
    val toInstall = ids.toSet() - installedIds
    warmUpPluginStates(ids.toSet())
    reopenRecentProjectsIfNeeded()
    val project = withContext(Dispatchers.EDT) {
      RestService.getLastFocusedOrOpenedProject() ?: ProjectManager.getInstance().defaultProject
    }
    val updates = if (installedIds.isEmpty()) InternalPluginResults.empty() else withContext(Dispatchers.IO) {
      withBackgroundProgress(project, IdeBundle.message("updates.checking.plugins")) {
        coroutineToIndicator { indicator ->
          UpdateCheckerFacade.getInstance().getPluginUpdates(installedIds, indicator)
        }
      }
    }
    val downloaders = updates.pluginUpdates.all
    if (toInstall.isEmpty() && downloaders.isEmpty()) {
      if (updates.errors.isNotEmpty()) {
        return updates.errors.entries.joinToString("\n") { entry ->
          val host = entry.key
          if (host == null) IdeBundle.message("updates.plugins.error.message1", entry.value.message)
          else IdeBundle.message("updates.plugins.error.message2", host, entry.value.message)
        }
      }
      val names = ids.joinToString(", ") {
        PluginManagerCore.getPlugin(it)?.name ?: it.idString
      }
      return IdeBundle.message("jb.protocol.plugin.install.already.installed", names)
    }

    val installedPlugins = withContext(Dispatchers.IO) {
      UiPluginManager.getInstance().findInstalledPlugins(installedIds)
    }
    withContext(Dispatchers.EDT) {
      AppIcon.getInstance().requestAttention(project, true)
      if (toInstall.isNotEmpty()) {
        installAndEnable(project, toInstall, showDialog = true, selectAlInDialog = true) { }
      }
      if (downloaders.isNotEmpty()) {
        val dialog = PluginUpdateDialog(project, downloaders.map { it.uiModel }, updates.pluginNods, installedPlugins)
        PluginUpdateDialog.showDialogAndUpdateDownloaders(downloaders, dialog)
      }
    }
    return null
  }

  // When the IDE is launched via jetbrains://tool/plugin/install with no instance running,
  // IdeStarter short-circuits the normal "reopen projects on startup" flow because a URI was
  // provided. Trigger the same reopen explicitly so the user's previous session is preserved.
  private suspend fun reopenRecentProjectsIfNeeded() {
    if (serviceAsync<ProjectManager>().openProjects.isNotEmpty()) return
    val recentProjectManager = serviceAsync<RecentProjectsManager>()
    if (!recentProjectManager.willReopenProjectOnStart()) return
    try {
      recentProjectManager.reopenLastProjectsOnStart()
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Throwable) {
      logger<InstallPluginJbProtocolCommand>().error("Cannot reopen recent projects before install dialog", e)
    }
  }

  // Loads plugin installation states off-EDT before opening the install dialog. On the JetBrains
  // Client the dialog's button refresh would otherwise hit a background-thread assertion while on EDT.
  private suspend fun warmUpPluginStates(ids: Set<PluginId>) {
    withContext(Dispatchers.IO) {
      val manager = UiPluginManager.getInstance()
      for (id in ids) {
        runCatching { manager.getPluginInstallationState(id) }
      }
    }
  }

  private fun collectPluginIds(parameters: Map<String, String>): List<PluginId> {
    val result = LinkedHashMap<String, PluginId>()
    parameters["id"]?.takeIf { it.isNotBlank() }?.let { result[it] = PluginId.getId(it) }
    parameters["ids"]?.splitToSequence(',')
      ?.map { it.trim() }
      ?.filter { it.isNotEmpty() }
      ?.forEach { result[it] = PluginId.getId(it) }
    return result.values.toList()
  }
}
