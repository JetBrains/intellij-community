// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.ide

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.newui.UiPluginManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.JBProtocolCommand
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.installAndEnable
import com.intellij.openapi.util.NlsContexts.DialogMessage
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.AppIcon
import com.intellij.xml.util.XmlStringUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Handles `jetbrains://tool/plugin/install?id=<pluginId>[&ids=<id1>,<id2>,…]` URLs.
 *
 * The marketplace confirmation dialog shown by [installAndEnable] is the user-consent gate;
 * we do not enforce a host allowlist here because OS-level protocol handlers do not provide
 * a verifiable origin.
 */
@Internal
class InstallPluginJbProtocolCommand : JBProtocolCommand("plugin") {
  override suspend fun execute(target: String?, parameters: Map<String, String>, fragment: String?): @DialogMessage String? {
    if (target != "install") {
      return IdeBundle.message("jb.protocol.unknown.target", target)
    }
    val ids = collectPluginIds(parameters)
    if (ids.isEmpty()) {
      return IdeBundle.message("jb.protocol.parameter.missing", "id")
    }

    val toInstall = ids.filterNot { PluginManagerCore.isPluginInstalled(it) }.toSet()
    if (toInstall.isEmpty()) {
      val names = ids.joinToString(", ") {
        HtmlChunk.text(PluginManagerCore.getPlugin(it)?.name ?: it.idString).bold().toString()
      }
      return XmlStringUtil.wrapInHtml(IdeBundle.message("jb.protocol.plugin.install.already.installed", names))
    }

    warmUpPluginStates(toInstall)
    withContext(Dispatchers.EDT) {
      val project = RestService.getLastFocusedOrOpenedProject() ?: ProjectManager.getInstance().defaultProject
      AppIcon.getInstance().requestAttention(project, true)
      installAndEnable(project, toInstall, showDialog = true, selectAlInDialog = true) { }
    }
    return null
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
