// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.intellij.ide.plugins.DisabledPluginsState
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginNode
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readLines

private const val AI_ASSISTANT_PLUGIN_ID = "com.intellij.ml.llm"
private const val SPACE_PLUGIN_ID = "com.jetbrains.space"
private const val OTHER_PLUGIN_ID = "org.example.other"
private const val CURRENT_VERSION = "2026.3"

/** [ConfigImportHelper.migrateImportedPlugins]: the plugin migrations of a partial import from the Import Settings dialog. */
internal class ImportedPluginsMigrationTest {
  /** The parent of the config directories of all versions, like `~/Library/Application Support/JetBrains`. */
  @TempDir
  lateinit var configRoot: Path

  private val newConfigDir: Path
    get() = configRoot.resolve("IntelliJIdea$CURRENT_VERSION")

  @BeforeEach
  fun createNewConfigDir() {
    newConfigDir.createDirectories()
  }

  @Test
  fun `a dry run lists the changes and writes no file`() {
    val result = migrateFrom("2026.2", dryRun = true)
    assertThat(ids(result.toMigrate)).containsExactly(AI_ASSISTANT_PLUGIN_ID, OTHER_PLUGIN_ID)
    assertThat(result.toDisable).containsExactly(PluginId.getId(AI_ASSISTANT_PLUGIN_ID))
    assertThat(newConfigDir.listDirectoryEntries()).isEmpty()
  }

  @Test
  fun `an import keeps AI Assistant to install and disables it`() {
    val result = migrateFrom("2026.2", dryRun = false)
    assertThat(ids(result.toMigrate)).containsExactly(AI_ASSISTANT_PLUGIN_ID, OTHER_PLUGIN_ID)
    assertThat(newConfigDir.resolve(DisabledPluginsState.DISABLED_PLUGINS_FILENAME).readLines()).containsExactly(AI_ASSISTANT_PLUGIN_ID)
    assertThat(newConfigDir.resolve(InitialConfigImportState.AI_ASSISTANT_DISABLED_BY_MIGRATION_MARKER)).exists()
  }

  @Test
  fun `an import from 2026_3 disables nothing`() {
    val result = migrateFrom("2026.3", dryRun = false)
    assertThat(ids(result.toMigrate)).containsExactly(AI_ASSISTANT_PLUGIN_ID, OTHER_PLUGIN_ID)
    assertThat(result.toDisable).isEmpty()
    assertThat(newConfigDir.listDirectoryEntries()).isEmpty()
  }

  @Test
  fun `an import that cannot write the disabled plugins file still returns the plugins`() {
    // a directory in place of the file makes the write fail
    newConfigDir.resolve(DisabledPluginsState.DISABLED_PLUGINS_FILENAME).createDirectories()
    val result = migrateFrom("2026.2", dryRun = false)
    assertThat(ids(result.toMigrate)).containsExactly(AI_ASSISTANT_PLUGIN_ID, OTHER_PLUGIN_ID)
    assertThat(result.toDisable).containsExactly(PluginId.getId(AI_ASSISTANT_PLUGIN_ID))
  }

  /** Every import offers AI Assistant, Space and one more plugin. A migration removes Space on any version. */
  private fun migrateFrom(previousVersion: String, dryRun: Boolean): ConfigImportHelper.PluginMigrationResult {
    val oldConfigDir = configRoot.resolve("IntelliJIdea$previousVersion").createDirectories()
    val plugins = listOf(plugin(AI_ASSISTANT_PLUGIN_ID), plugin(SPACE_PLUGIN_ID), plugin(OTHER_PLUGIN_ID))
    return ConfigImportHelper.migrateImportedPlugins(
      oldConfigDir, newConfigDir, plugins, CURRENT_VERSION, dryRun, logger<ImportedPluginsMigrationTest>()
    )
  }

  private fun plugin(id: String): IdeaPluginDescriptor = PluginNode(PluginId.getId(id))

  private fun ids(plugins: List<IdeaPluginDescriptor>): List<String> = plugins.map { it.pluginId.idString }
}
