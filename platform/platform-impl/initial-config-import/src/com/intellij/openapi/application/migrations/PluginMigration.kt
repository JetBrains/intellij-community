// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.migrations

import com.intellij.ide.plugins.PluginNode
import com.intellij.openapi.application.PluginMigrationOptions
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId

/**
 * Note that migrations are not taken into account for IDE updates through Toolbox
 */
internal abstract class PluginMigration {
  fun migratePlugins(options: PluginMigrationOptions) {
    migratePlugins(PluginMigrationDescriptor(options))
  }

  abstract fun migratePlugins(descriptor: PluginMigrationDescriptor)

  class PluginMigrationDescriptor(val options: PluginMigrationOptions) {
    val currentPluginsToDownload: Set<String> by lazy { getPluginIDs(options.pluginsToDownload) }
    val currentPluginsToMigrate: Set<String> by lazy { getPluginIDs(options.pluginsToMigrate) }

    private fun getPluginIDs(plugins: Collection<PluginDescriptor>) = plugins.map { it.pluginId.idString }.toSet()

    fun addPluginIfNeeded(pluginIdString: String) {
      if (!currentPluginsToDownload.contains(pluginIdString)) {
        options.pluginsToDownload.add(PluginNode(PluginId.getId(pluginIdString)))
      }
    }

    fun removePlugin(pluginIdString: String) {
      options.pluginsToMigrate.removeIf { it.pluginId.idString == pluginIdString }
    }

    fun removePluginToDownload(pluginIdString: String) {
      options.pluginsToDownload.removeIf { it.pluginId.idString == pluginIdString }
    }
  }
}
