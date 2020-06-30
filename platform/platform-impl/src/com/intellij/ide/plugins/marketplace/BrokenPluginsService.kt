// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.marketplace

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.util.BuildNumber
import com.intellij.util.execution.ParametersListUtil
import java.io.File

object BrokenPluginsService {
  private val marketplaceClient = MarketplaceRequests.getInstance()

  fun setupUpdateBrokenPlugins() {
    if (System.getProperty("idea.ignore.disabled.plugins") != null) return
    ApplicationManager.getApplication().executeOnPooledThread {
      updateBrokenPlugin()
    }
  }

  private fun updateBrokenPlugin() {
    val file = File(PluginManagerCore.MARKETPLACE_INCOMPATIBLE_PLUGINS)
    val isNotUpdatedPlugins = marketplaceClient.isFileNotModified(marketplaceClient.BROKEN_PLUGIN_PATH, marketplaceClient.getBrokenPluginsFile())
    if (isNotUpdatedPlugins) return
    val brokenPlugins = readBrokenPlugins()
    if (brokenPlugins.isEmpty()) return
    file.writeText(
      brokenPlugins.entries
        .joinToString("\n") { plugin -> "${plugin.key} ${plugin.value.joinToString(" ") { it }}" }
    )
    PluginManagerCore.setUpNeedToUpdateBrokenPlugins()
  }

  private fun readBrokenPlugins(): Map<String, Set<String>> {
    val allBrokenPlugins = marketplaceClient.getBrokenPlugins()
    val currentBuild = ApplicationInfoImpl.getInstance().build
    val currentBrokenPlugins = allBrokenPlugins
      .filter {
        val originalUntil = BuildNumber.fromString(it.originalUntil) ?: currentBuild
        val originalSince = BuildNumber.fromString(it.originalSince) ?: currentBuild
        val until = BuildNumber.fromString(it.until) ?: currentBuild
        val since = BuildNumber.fromString(it.since) ?: currentBuild
        (currentBuild in originalSince..originalUntil) && (currentBuild !in since..until)
      }
      .groupBy { it.id }.entries
      .associate { (pluginId, brokenPlugins) ->
        val versions = brokenPlugins.map { it.version.escapeIfSpaceContains() }.toSet()
        pluginId.escapeIfSpaceContains() to versions
      }
    return currentBrokenPlugins
  }

  private fun String.escapeIfSpaceContains() = if (this.contains(" ")) ParametersListUtil.escape(this) else this

}
