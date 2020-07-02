// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.marketplace

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.BuildNumber

object BrokenPluginsService {
  private val marketplaceClient = MarketplaceRequests.getInstance()

  fun setupUpdateBrokenPlugins() {
    if (System.getProperty("idea.ignore.disabled.plugins") != null) return
    ApplicationManager.getApplication().executeOnPooledThread {
      updateBrokenPlugin()
    }
  }

  private fun updateBrokenPlugin() {
    val isNotUpdatedPlugins = marketplaceClient.isFileNotModified(
      marketplaceClient.BROKEN_PLUGIN_PATH,
      marketplaceClient.getBrokenPluginsFile()
    )
    if (isNotUpdatedPlugins) return
    val brokenPlugins = getBrokenPlugins()
    if (brokenPlugins.isEmpty()) return
    PluginManagerCore.updateBrokenPlugins(brokenPlugins)
  }

  private fun getBrokenPlugins(): Map<PluginId, Set<String>> {
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
        PluginId.getId(pluginId) to brokenPlugins.map { it.version }.toSet()
      }
    return currentBrokenPlugins
  }

}
