// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment", "GrazieInspection")

package org.jetbrains.intellij.build.productLayout.config

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.PluginId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

/**
 * Suppressions for a content module (module declared in plugin's `<content>` section).
 *
 * Groups all suppressions for a single content module in one place.
 */
@Serializable
data class ContentModuleSuppression(
  /** Module names to suppress from the descriptor's `<dependencies>` */
  @JvmField val suppressModules: Set<ContentModuleName> = emptySet(),
  /** Plugin IDs to suppress from the descriptor's `<depends>` elements */
  @JvmField val suppressPlugins: Set<PluginId> = emptySet(),
  /**
   * Library names to NOT replace with library modules in the IML file.
   *
   * Auto-populated by `--update-suppressions`. Remove entries one-by-one to enable
   * replacements incrementally.
   *
   * Example: `["kotlin-test"]` prevents replacing `kotlin-test` library
   * with `intellij.libraries.kotlinTest` module in this module's IML.
   */
  @JvmField val suppressLibraries: Set<String> = emptySet(),
  /**
   * Test library names to NOT change scope to TEST in the IML file.
   *
   * Auto-populated by `--update-suppressions`. Remove entries one-by-one to enable
   * scope changes incrementally.
   *
   * Use for test framework modules that legitimately need test libraries
   * in production scope (e.g., PROVIDED for `intellij.tools.ide.starter.junit5`).
   */
  @JvmField val suppressTestLibraryScope: Set<String> = emptySet(),
)

/**
 * Suppressions for a plugin (plugin.xml level).
 */
@Serializable
data class PluginSuppression(
  /** Module names to suppress from the plugin.xml's `<dependencies>` as `<module name="..."/>` */
  @JvmField val suppressModules: Set<ContentModuleName> = emptySet(),
  /** Plugin IDs to suppress from the plugin.xml's `<dependencies>` as `<plugin id="..."/>` */
  @JvmField val suppressPlugins: Set<PluginId> = emptySet(),
  /** Allow plugin.xml without <id> element for this plugin target */
  @JvmField val allowMissingPluginId: Boolean = false,
)

/**
 * Validation exceptions for a content module.
 */
@Serializable
data class ValidationException(
  /** Plugin IDs that are allowed to be "missing" in validation */
  @JvmField val allowMissingPlugins: Set<PluginId> = emptySet(),
)

/**
 * Configuration for suppressing auto-generated module/plugin dependencies.
 *
 * **Unified structure:** All suppressions for a module/plugin are grouped together.
 *
 * **Usage:** Loaded at pipeline startup, used by generators to filter dependencies.
 *
 * **Updates:** Automatically updated when running `bazel run //platform/buildScripts:plugin-model-tool`.
 * When running packaging tests (commitChanges=false), stale entries cause "file out of sync" errors.
 */
@Serializable
data class SuppressionConfig(
  /**
   * Content module suppressions (unified).
   *
   * Key: Content module name (e.g., "intellij.python.junit5Tests")
   * Value: All suppressions for this module (modules + plugins)
   */
  @JvmField val contentModules: Map<ContentModuleName, ContentModuleSuppression> = emptyMap(),

  /**
   * Plugin suppressions (for plugin.xml files).
   *
   * Key: Plugin module name (e.g., "intellij.cidr.clangd")
   * Value: Suppressions for this plugin
   */
  @JvmField val plugins: Map<ContentModuleName, PluginSuppression> = emptyMap(),

  /**
   * Validation exceptions.
   *
   * Key: Module name (e.g., "intellij.spring.boot")
   * Value: Exceptions for validation (NOT suppressions - these deps exist but are allowed to be unresolved)
   */
  @JvmField val validationExceptions: Map<ContentModuleName, ValidationException> = emptyMap(),

  /**
   * Direct error key suppression for pipeline errors.
   *
   * Keys match [org.jetbrains.intellij.build.productLayout.model.error.ValidationError.suppressionKey].
   * Most keys come from [org.jetbrains.intellij.build.productLayout.model.error.UnsuppressedPipelineError.errorKey].
   * Example key: `"nonStandardRoot:intellij.fullLine.yaml"`
   *
   * **Note:** Stale suppressions (entries in this config that no longer apply) are NOT reported
   * as errors. They're handled via the file sync pattern: when suppressions become stale,
   * the generator auto-removes them (`commitChanges=true`) or reports as
   * [org.jetbrains.intellij.build.productLayout.model.error.FileDiff]
   * (`commitChanges=false` in packaging tests).
   */
  @JvmField val suppressedErrors: Set<String> = emptySet(),
) {
  /**
   * Check if a pipeline error key is suppressed.
   *
   * @param errorKey The error key from [org.jetbrains.intellij.build.productLayout.model.error.UnsuppressedPipelineError.errorKey]
   * @return true if the error should be suppressed (not reported)
   */
  fun isSuppressed(errorKey: String): Boolean {
    if (errorKey.startsWith(MISSING_PLUGIN_ID_PREFIX)) {
      val pluginName = errorKey.removePrefix(MISSING_PLUGIN_ID_PREFIX)
      if (plugins[ContentModuleName(pluginName)]?.allowMissingPluginId == true) {
        return true
      }
    }
    return errorKey in suppressedErrors
  }
  /** Get suppressed module deps for a content module */
  fun getSuppressedModules(contentModuleName: ContentModuleName): Set<ContentModuleName> {
    return contentModules.get(contentModuleName)?.suppressModules ?: emptySet()
  }

  /** Get suppressed plugin deps for a content module */
  fun getSuppressedPlugins(contentModuleName: ContentModuleName): Set<PluginId> {
    return contentModules.get(contentModuleName)?.suppressPlugins ?: emptySet()
  }

  /** Get suppressed module deps for a plugin.xml */
  fun getPluginSuppressedModules(pluginContentModuleName: ContentModuleName): Set<ContentModuleName> {
    return plugins.get(pluginContentModuleName)?.suppressModules ?: emptySet()
  }

  /** Get suppressed plugin deps for a plugin.xml */
  fun getPluginSuppressedPlugins(pluginContentModuleName: ContentModuleName): Set<PluginId> {
    return plugins.get(pluginContentModuleName)?.suppressPlugins ?: emptySet()
  }

  /** Get allowed missing plugin deps for validation */
  fun getAllowedMissingPlugins(contentModuleName: ContentModuleName): Set<PluginId> {
    val explicit = validationExceptions.get(contentModuleName)?.allowMissingPlugins ?: emptySet()
    // Auto-infer from suppressPlugins - if we suppress adding a plugin dep, validation error is expected
    val fromSuppressPlugins = contentModules.get(contentModuleName)?.suppressPlugins ?: emptySet()
    return explicit + fromSuppressPlugins
  }

  /** Get all allowed missing plugins as a map (for validation APIs that need Map<ModuleName, Set<PluginId>>) */
  fun getAllowedMissingPluginsMap(): Map<ContentModuleName, Set<PluginId>> {
    // Combine explicit validationExceptions with auto-inferred from suppressPlugins
    val allModules = validationExceptions.keys + contentModules.keys
    val result = HashMap<ContentModuleName, Set<PluginId>>()
    for (moduleName in allModules) {
      val allowed = getAllowedMissingPlugins(moduleName)
      if (allowed.isNotEmpty()) {
        result.put(moduleName, allowed)
      }
    }
    return result
  }

  companion object {
    private const val MISSING_PLUGIN_ID_PREFIX = "missing-plugin-id:"
    private val json = Json {
      prettyPrint = true
      prettyPrintIndent = "  "  // 2-space indent
      encodeDefaults = false
      ignoreUnknownKeys = true  // Allow reading both old and new formats
    }

    /**
     * Loads suppression config from the specified path.
     *
     * @param path Path to suppressions.json, or null to return empty config
     * @return Config if file exists, empty config if path is null
     */
    fun load(path: Path?): SuppressionConfig {
      if (path == null) {
        return SuppressionConfig()
      }
      val content = Files.readString(path)
      return json.decodeFromString<SuppressionConfig>(content)
    }

    /**
     * Saves suppression config to the specified path (new unified format only).
     */
    fun save(path: Path, config: SuppressionConfig) {
      Files.createDirectories(path.parent)
      Files.writeString(path, serializeToString(config))
    }

    /**
     * Serializes suppression config to a JSON string.
     * Used by SuppressionConfigGenerator for comparing with existing file content.
     */
    fun serializeToString(config: SuppressionConfig): String {
      return json.encodeToString(config)
    }
  }
}
