// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.migrations

internal class RustMigration262 : PluginMigration() {
  override fun migratePlugins(descriptor: PluginMigrationDescriptor) {
    if (descriptor.currentPluginsToMigrate.contains(RUST_PLUGIN_ID) ||
        descriptor.currentPluginsToDownload.contains(RUST_PLUGIN_ID)) {
      descriptor.addPluginIfNeeded(NATIVE_DEBUG_PLUGIN_ID)
    }
  }
}

private const val RUST_PLUGIN_ID = "com.jetbrains.rust"
private const val NATIVE_DEBUG_PLUGIN_ID = "com.intellij.nativeDebug"
