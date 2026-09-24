// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.migrations

private val OBSOLETE_BDT_PLUGIN_IDS = listOf(
  "intellij.bigdatatools.coreUi",
  "intellij.bigdatatools.awsBase",
  "intellij.bigdatatools.azure",
  "intellij.bigdatatools.gcloud",
)

internal class BigDataToolsMigration263 : PluginMigration() {
  override fun migratePlugins(descriptor: PluginMigrationDescriptor) {
    for (pluginId in OBSOLETE_BDT_PLUGIN_IDS) {
      descriptor.removePlugin(pluginId)
      descriptor.removePluginToDownload(pluginId)
    }
  }
}
