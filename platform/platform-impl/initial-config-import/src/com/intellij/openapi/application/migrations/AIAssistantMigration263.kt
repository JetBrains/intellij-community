// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.migrations

import com.intellij.openapi.util.text.StringUtil

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
    }
    else {
      descriptor.disablePlugin(AI_ASSISTANT_PLUGIN_ID)
      options.log.info("AI Assistant disabled: settings imported from $previousVersion")
    }
  }
}
