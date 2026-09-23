// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.migrations

import com.intellij.ide.plugins.writePluginStringSet
import com.intellij.openapi.project.impl.processPerProjectSupport
import com.intellij.openapi.util.text.StringUtil
import java.io.IOException
import java.nio.file.Files

private const val AI_ASSISTANT_PLUGIN_ID = "com.intellij.ml.llm"

/** The first version where AIR replaces AI Assistant. */
private const val AIR_VERSION = "2026.3"

/**
 * AIR replaces AI Assistant: when settings are imported from a version earlier than [AIR_VERSION], AI Assistant is added
 * to the disabled plugins of the new configuration. The user can enable it again in the Plugins settings; a later import
 * (from 2026.3 or newer) keeps that choice.
 */
internal class AIAssistantMigration263 : PluginMigration() {
  override fun migratePlugins(descriptor: PluginMigrationDescriptor) {
    val options = descriptor.options
    val previousVersion = options.previousVersion
    if (previousVersion == null || StringUtil.compareVersionNumbers(previousVersion, AIR_VERSION) >= 0) {
      options.log.info("AI Assistant migration skipped: previous version is $previousVersion")
      return
    }

    // the old configuration, including its disabled plugins file, is already copied to the new one at this point
    val file = options.newConfigDir.resolve(processPerProjectSupport().disabledPluginsFileName)
    try {
      val disabledPlugins = LinkedHashSet<String>()
      if (Files.exists(file)) {
        Files.readAllLines(file).mapNotNullTo(disabledPlugins) { it.trim().takeIf(String::isNotEmpty) }
      }
      if (!disabledPlugins.add(AI_ASSISTANT_PLUGIN_ID)) {
        options.log.info("AI Assistant migration skipped: the plugin was already disabled")
        return
      }
      writePluginStringSet(file, disabledPlugins)
      options.log.info("AI Assistant disabled: settings imported from $previousVersion")
    }
    catch (e: IOException) {
      options.log.warn("AI Assistant migration failed: cannot update $file", e)
    }
  }
}
