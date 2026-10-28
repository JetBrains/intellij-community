// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.migrations

import com.intellij.ide.plugins.DisabledPluginsState
import com.intellij.openapi.application.InitialConfigImportState
import com.intellij.openapi.util.text.StringUtil
import java.io.IOException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path

private const val AI_ASSISTANT_PLUGIN_ID = "com.intellij.ml.llm"

/** The first version where AIR replaces AI Assistant. */
private const val AIR_VERSION = "2026.3"

/**
 * AIR replaces AI Assistant: when settings are imported from a version earlier than [AIR_VERSION], AI Assistant is added
 * to the disabled plugins of the new configuration. The user can enable it again in the Plugins settings; a later import
 * (from 2026.3 or newer) keeps that choice.
 *
 * When this import disables an AI Assistant that the user had enabled, it also creates the empty marker file
 * [InitialConfigImportState.AI_ASSISTANT_DISABLED_BY_MIGRATION_MARKER] in the new config directory. The AI Assistant
 * installer shows the offboarding banner while the marker exists, and deletes it when the user makes a choice.
 */
internal class AIAssistantMigration263 : PluginMigration() {
  override fun migratePlugins(descriptor: PluginMigrationDescriptor) {
    val options = descriptor.options
    val previousVersion = options.previousVersion
    if (previousVersion == null || StringUtil.compareVersionNumbers(previousVersion, AIR_VERSION) >= 0) {
      options.log.info("AI Assistant migration skipped: previous version is $previousVersion")
      return
    }
    descriptor.disablePlugin(AI_ASSISTANT_PLUGIN_ID)
    options.log.info("AI Assistant disabled: settings imported from $previousVersion")
    if (options.dryRun) {
      return
    }
    try {
      writeOffboardingMarker(descriptor)
    }
    catch (e: IOException) {
      options.log.warn("Cannot write the AI Assistant offboarding marker", e)
    }
  }

  private fun writeOffboardingMarker(descriptor: PluginMigrationDescriptor) {
    val options = descriptor.options
    val hadAssistant = AI_ASSISTANT_PLUGIN_ID in descriptor.currentPluginsToMigrate ||
                       AI_ASSISTANT_PLUGIN_ID in descriptor.currentPluginsToDownload
    if (!hadAssistant) {
      options.log.info("AI Assistant offboarding marker skipped: the old installation had no AI Assistant")
      return
    }
    if (wasDisabled(options.oldConfigDir)) {
      options.log.info("AI Assistant offboarding marker skipped: the old installation had AI Assistant disabled")
      return
    }
    val marker = options.newConfigDir.resolve(InitialConfigImportState.AI_ASSISTANT_DISABLED_BY_MIGRATION_MARKER)
    Files.createFile(marker)
    options.log.info("AI Assistant offboarding marker written: $marker")
  }

  /** Reads the old file directly: [DisabledPluginsState.loadDisabledPlugins] rewrites the file that it reads. */
  private fun wasDisabled(oldConfigDir: Path): Boolean {
    val disabledPlugins = oldConfigDir.resolve(DisabledPluginsState.DISABLED_PLUGINS_FILENAME)
    return try {
      Files.readAllLines(disabledPlugins).any { it.trim() == AI_ASSISTANT_PLUGIN_ID }
    }
    catch (_: NoSuchFileException) {
      false
    }
  }
}
