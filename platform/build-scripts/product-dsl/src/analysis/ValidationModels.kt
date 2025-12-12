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
  @JvmField val missingDependencies: Map<String, Set<String>>,
) : ValidationError

data class MissingModuleSetsError(
  override val context: String,
  @JvmField val missingModuleSets: Set<String>,
) : ValidationError

data class DuplicateModulesError(
  override val context: String,
  @JvmField val duplicates: Map<String, Int>,
) : ValidationError

data class MissingDependenciesError(
  override val context: String,
  @JvmField val missingModules: Map<String, Set<String>>,
  @JvmField val allModuleSets: List<ModuleSet>,
  /** Unified source info for all modules (needing and missing) */
  @JvmField val moduleSourceInfo: Map<String, ModuleSourceInfo> = emptyMap(),
) : ValidationError

data class XIncludeResolutionError(
  override val context: String,
  /** Plugin module name where xi:include was found */
  @JvmField val pluginName: String,
  /** xi:include path that failed to resolve */
  @JvmField val xIncludePath: String,
  /** Internal debug info (search details) */
  @JvmField val debugInfo: String,
) : ValidationError

// endregion

// region Module Source Info

/**
 * Complete source info for a module - works for both plugin content modules and module set modules.
 * Single lookup provides all context for error messages.
 * Built once and shared across all validation lookups.
 */
data class ModuleSourceInfo(
  /** The loading mode (EMBEDDED, REQUIRED, OPTIONAL, ON_DEMAND) */
  @JvmField val loadingMode: ModuleLoadingRuleValue? = null,
  /** Plugin containing this module, or null if from module set directly */
  @JvmField val sourcePlugin: String? = null,
  /** Module set containing this module, or null if from bundled plugin */
  @JvmField val sourceModuleSet: String? = null,
  /** Products that contain this module */
  @JvmField val sourceProducts: Set<String> = emptySet(),
  /** Source description if plugin is in additionalPlugins */
  @JvmField val additionalPluginSource: String? = null,
)

// endregion

// region Structured Output for Tests

/**
 * Result of model generator validation.
 * Contains both file diffs (out-of-sync files) and validation errors (missing dependencies, etc.).
 */
data class ModelValidationResult(
  @JvmField val diffs: List<DryRunDiff>,
  @JvmField val errors: List<ValidationError>,
)

// endregion
