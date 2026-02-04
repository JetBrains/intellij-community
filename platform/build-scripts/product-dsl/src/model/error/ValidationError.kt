// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.model.error

import org.jetbrains.intellij.build.productLayout.stats.AnsiStyle

/**
 * Category of validation/pipeline error for grouping and filtering.
 *
 * Each category maps to one or more [ValidationError] subclasses.
 * Categories marked "suppressible" can be filtered via `suppressions.json`.
 */
enum class ErrorCategory {
  // Structural (always hard fail)
  /** [DuplicateModulesError] - hard failure, not suppressible */
  DUPLICATE_MODULES,
  /** [MissingModuleSetsError] - hard failure, not suppressible */
  MISSING_MODULE_SETS,
  /** [SelfContainedValidationError] - hard failure, not suppressible */
  SELF_CONTAINED_VIOLATION,

  // Plugin validation
  /** [PluginDependencyError] - hard failure, not suppressible */
  PLUGIN_DEPENDENCY_UNRESOLVED,
  /** [MissingContentModulePluginDependencyError] - hard failure, not suppressible */
  CONTENT_MODULE_PLUGIN_DEP_MISSING,
  /** [DuplicatePluginContentModulesError] - hard failure, not suppressible */
  PLUGIN_CONTENT_DUPLICATE,
  /** [PluginDescriptorIdConflictError] - hard failure, not suppressible */
  PLUGIN_DESCRIPTOR_ID_CONFLICT,
  /** [PluginDependencyNotBundledError] - hard failure, not suppressible */
  PLUGIN_PLUGIN_DEP_MISSING,
  /** [DuplicatePluginDependencyDeclarationError] - hard failure, not suppressible */
  PLUGIN_PLUGIN_DEP_DUPLICATE,
  /** [DslTestPluginDependencyError] - hard failure, not suppressible */
  DSL_TEST_PLUGIN_DEPENDENCY_UNRESOLVED,
  /** [MissingTestPluginPluginDependencyError] - hard failure, not suppressible */
  TEST_PLUGIN_PLUGIN_DEP_MISSING,

  // Content module backing
  /** [MissingContentModuleBackingError] - hard failure, not suppressible */
  CONTENT_MODULE_BACKING_MISSING,

  // Config validation
  /** [InvalidSuppressionConfigKeyError] - hard failure, not suppressible */
  INVALID_SUPPRESSION_KEY,
  /** [MissingPluginInGraphError] - hard failure, not suppressible */
  MISSING_PLUGIN_IN_GRAPH,
  /** [MissingPluginIdError] - suppressible via plugins.allowMissingPluginId */
  MISSING_PLUGIN_ID,
  /** [DuplicateDslTestPluginIdError] - hard failure, not suppressible */
  DUPLICATE_DSL_TEST_PLUGIN_ID,

  // File-level
  /** [FileDiff] - reported when commitChanges=false and files out of sync */
  FILE_DIFF,
  /** [UnsuppressedPipelineError] with this category - suppressible via suppressedErrors */
  NON_STANDARD_DESCRIPTOR_ROOT,
  /** [XIncludeResolutionError] - hard failure, not suppressible */
  XI_INCLUDE_RESOLUTION,

  // Missing dependencies
  /** [MissingDependenciesError] - hard failure, not suppressible */
  MISSING_DEPENDENCY,
}

/**
 * Unified error type for all pipeline errors.
 *
 * Errors can be:
 * - **Hard failures** ([suppressionKey] = null): Always reported, cannot be suppressed
 * - **Suppressible** ([suppressionKey] != null): Can be suppressed via `suppressions.json`
 *
 * Each error knows how to format itself via [format], eliminating external formatter functions.
 */
sealed interface ValidationError {
  val context: String

  /** Error category for grouping in output */
  val category: ErrorCategory

  /** Name of the validation rule that produced this error (for debugging) */
  val ruleName: String

  /**
   * Suppression key for this error, or null if not suppressible.
   * Errors without suppression key are HARD FAILURES that always block.
   */
  val suppressionKey: String? get() = null

  /**
   * Format this error for human-readable output with ANSI color support.
   */
  fun format(s: AnsiStyle): String
}

/**
 * Returns a short identifier for test naming.
 */
fun ValidationError.errorId(): String {
  return when (this) {
    is FileDiff -> "diff:${path.fileName}"
    is XIncludeResolutionError -> "xinclude:$pluginName:$xIncludePath"
    is MissingDependenciesError -> "missing-deps:$context"
    is MissingModuleSetsError -> "missing-sets:$context"
    is DuplicateModulesError -> "duplicates:$context"
    is SelfContainedValidationError -> "self-contained:$context"
    is MissingContentModulePluginDependencyError -> "missing-plugin-dep:$context"
    is DuplicatePluginContentModulesError -> "plugin-content-dup:$context"
    is PluginDescriptorIdConflictError -> "plugin-descriptor-id-conflict:$context"
    is PluginDependencyError -> "plugin-dep:${pluginName.value}"
    is PluginDependencyNotBundledError -> "plugin-plugin-dep:${pluginName.value}"
    is DuplicatePluginDependencyDeclarationError -> "plugin-plugin-dep-dup:${pluginName.value}"
    is DslTestPluginDependencyError -> "dsl-test-plugin-dep:${testPluginId.value}"
    is MissingTestPluginPluginDependencyError -> "test-plugin-missing-plugin-dep:${testPluginId.value}"
    is InvalidSuppressionConfigKeyError -> "invalid-suppression-keys:$context"
    is UnsuppressedPipelineError -> "pipeline:$category:$errorKey"
    is MissingPluginInGraphError -> "missing-plugin:$productName:${pluginName.value}"
    is MissingPluginIdError -> "missing-plugin-id:${pluginName.value}"
    is DuplicateDslTestPluginIdError -> "dsl-test-plugin-id-dup:${pluginId.value}"
    is MissingContentModuleBackingError -> "content-module-backing:$context"
  }
}
