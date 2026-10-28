// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.migrations

import com.intellij.ide.plugins.DisabledPluginsState
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginNode
import com.intellij.openapi.application.InitialConfigImportState
import com.intellij.openapi.application.PluginMigrationOptions
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeLines

private const val AI_ASSISTANT_PLUGIN_ID = "com.intellij.ml.llm"
private const val CURRENT_VERSION = "2026.3"

internal class AIAssistantMigration263Test {
  /** The parent of the config directories of all versions, like `~/Library/Application Support/JetBrains`. */
  @TempDir
  lateinit var configRoot: Path

  private val aiAssistantId = PluginId.getId(AI_ASSISTANT_PLUGIN_ID)

  private val newConfigDir: Path
    get() = configRoot.resolve("IntelliJIdea$CURRENT_VERSION")

  private val marker: Path
    get() = newConfigDir.resolve(InitialConfigImportState.AI_ASSISTANT_DISABLED_BY_MIGRATION_MARKER)

  @BeforeEach
  fun createNewConfigDir() {
    newConfigDir.createDirectories()
  }

  @Test
  fun `disables AI Assistant when settings come from 2026_2`() {
    val options = migrateFrom("2026.2")
    assertThat(options.pluginsToDisable).containsExactly(aiAssistantId)
  }

  @Test
  fun `writes the marker for a migrated AI Assistant`() {
    val options = migrateFrom("2026.2", pluginsToMigrate = listOf(plugin(AI_ASSISTANT_PLUGIN_ID)))
    assertThat(options.pluginsToDisable).containsExactly(aiAssistantId)
    assertThat(marker).exists()
  }

  @Test
  fun `writes the marker for a downloaded AI Assistant`() {
    val options = migrateFrom("2025.3", pluginsToDownload = listOf(plugin(AI_ASSISTANT_PLUGIN_ID)))
    assertThat(options.pluginsToDisable).containsExactly(aiAssistantId)
    assertThat(marker).exists()
  }

  @Test
  fun `writes the marker when the old installation disabled other plugins only`() {
    migrateFrom("2026.2", pluginsToMigrate = listOf(plugin(AI_ASSISTANT_PLUGIN_ID)), oldDisabledPlugins = listOf("org.example.other"))
    assertThat(marker).exists()
  }

  @Test
  fun `writes no marker when the old installation had no AI Assistant`() {
    val options = migrateFrom("2026.2", pluginsToMigrate = listOf(plugin("org.example.other")))
    assertThat(options.pluginsToDisable).containsExactly(aiAssistantId)
    assertThat(marker).doesNotExist()
  }

  @Test
  fun `writes no marker when the old installation had AI Assistant disabled`() {
    val options = migrateFrom(
      "2026.2",
      pluginsToMigrate = listOf(plugin(AI_ASSISTANT_PLUGIN_ID)),
      oldDisabledPlugins = listOf("org.example.other", "  $AI_ASSISTANT_PLUGIN_ID  "),
    )
    assertThat(options.pluginsToDisable).containsExactly(aiAssistantId)
    assertThat(marker).doesNotExist()
  }

  @Test
  fun `writes no marker in a dry run`() {
    val options = migrateFrom("2026.2", pluginsToMigrate = listOf(plugin(AI_ASSISTANT_PLUGIN_ID)), dryRun = true)
    assertThat(options.pluginsToDisable).containsExactly(aiAssistantId)
    assertThat(marker).doesNotExist()
  }

  @Test
  fun `leaves settings from 2026_3 alone`() {
    val options = migrateFrom("2026.3", pluginsToMigrate = listOf(plugin(AI_ASSISTANT_PLUGIN_ID)))
    assertThat(options.pluginsToDisable).isEmpty()
    assertThat(marker).doesNotExist()
  }

  @Test
  fun `leaves settings from a later version alone`() {
    val options = migrateFrom("2027.1", pluginsToMigrate = listOf(plugin(AI_ASSISTANT_PLUGIN_ID)))
    assertThat(options.pluginsToDisable).isEmpty()
    assertThat(marker).doesNotExist()
  }

  @Test
  fun `leaves settings alone when the previous version is unknown`() {
    val options = migrateFrom(null, pluginsToMigrate = listOf(plugin(AI_ASSISTANT_PLUGIN_ID)))
    assertThat(options.pluginsToDisable).isEmpty()
    assertThat(marker).doesNotExist()
  }

  /** The old config directory is a sibling of the new one, and its name carries the version, as in the real layout. */
  private fun migrateFrom(
    previousVersion: String?,
    pluginsToMigrate: List<IdeaPluginDescriptor> = emptyList(),
    pluginsToDownload: List<IdeaPluginDescriptor> = emptyList(),
    oldDisabledPlugins: List<String>? = null,
    dryRun: Boolean = false,
  ): PluginMigrationOptions {
    val oldConfigDir = configRoot.resolve("IntelliJIdea${previousVersion ?: "Unknown"}").createDirectories()
    if (oldDisabledPlugins != null) {
      oldConfigDir.resolve(DisabledPluginsState.DISABLED_PLUGINS_FILENAME).writeLines(oldDisabledPlugins)
    }
    val options = PluginMigrationOptions(
      previousVersion = previousVersion,
      currentProductVersion = CURRENT_VERSION,
      newConfigDir = newConfigDir,
      oldConfigDir = oldConfigDir,
      pluginsToMigrate = pluginsToMigrate.toMutableList(),
      pluginsToDownload = pluginsToDownload.toMutableList(),
      pluginsToDisable = mutableListOf(),
      log = logger<AIAssistantMigration263Test>(),
      dryRun = dryRun,
    )
    AIAssistantMigration263().migratePlugins(options)
    return options
  }

  private fun plugin(id: String): IdeaPluginDescriptor = PluginNode(PluginId.getId(id))
}
