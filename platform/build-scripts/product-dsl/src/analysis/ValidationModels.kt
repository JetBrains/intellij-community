// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.analysis

import com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRuleValue
import org.jetbrains.intellij.build.productLayout.ModuleSet
import org.jetbrains.intellij.build.productLayout.util.DryRunDiff

// region Validation Error Types

sealed interface ValidationError {
  val context: String
}

data class SelfContainedValidationError(
  override val context: String,
  val missingDependencies: Map<String, Set<String>>,
) : ValidationError

data class MissingModuleSetsError(
  override val context: String,
  val missingModuleSets: Set<String>,
) : ValidationError

data class DuplicateModulesError(
  override val context: String,
  val duplicates: Map<String, Int>,
) : ValidationError

data class MissingDependenciesError(
  override val context: String,
  val missingModules: Map<String, Set<String>>,
  val allModuleSets: List<ModuleSet>,
  /** Metadata about modules that have missing dependencies (loading mode, source plugin, etc.) */
  val moduleMetadata: Map<String, ModuleMetadata> = emptyMap(),
  /** Full traceability info for all plugin modules (for looking up missing dep info) */
  val moduleTraceInfo: Map<String, ModuleTraceInfo> = emptyMap(),
) : ValidationError

// endregion

// region Module Metadata

/**
 * Metadata about a module's origin and loading characteristics.
 * Used to provide contextual information in validation error messages.
 */
data class ModuleMetadata(
  /** The loading mode (EMBEDDED, REQUIRED, OPTIONAL, ON_DEMAND) */
  val loadingMode: ModuleLoadingRuleValue?,
  /** Name of the bundled plugin that contributed this module, or null if from module set */
  val sourcePlugin: String?,
  /** Name of the module set that contributed this module, or null if from bundled plugin */
  val sourceModuleSet: String?,
  /** Products that contain this module (for global validation error messages) */
  val sourceProducts: Set<String>? = null,
)

/**
 * Complete traceability info for a plugin content module.
 * Single lookup provides all context for error messages.
 * Built once and shared across all validation lookups.
 */
data class ModuleTraceInfo(
  /** Plugin containing this module */
  @JvmField val sourcePlugin: String,
  /** Products that bundle this plugin (empty for additional/non-bundled plugins) */
  @JvmField val bundledInProducts: Set<String>,
  /** Source description if plugin is in additionalPlugins, null otherwise */
  @JvmField val additionalPluginSource: String?,
)

// endregion

// region Structured Output for Tests

/**
 * Represents a single validation error for external consumers (e.g., test frameworks).
 * Each error corresponds to one missing dependency or validation issue.
 */
data class StructuredValidationError(
  /** Short identifier for the error (e.g., "missing-dep:intellij.javascript.parser") */
  @JvmField val id: String,
  /** Plain-text error message without ANSI codes or ASCII art */
  @JvmField val message: String,
)

/**
 * Result of model generator validation.
 * Contains both file diffs (out-of-sync files) and validation errors (missing dependencies, etc.).
 */
data class ModelValidationResult(
  @JvmField val diffs: List<DryRunDiff>,
  @JvmField val errors: List<StructuredValidationError>,
)

// endregion
