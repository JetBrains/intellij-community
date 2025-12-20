// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.validation

import com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRuleValue
import org.jetbrains.intellij.build.productLayout.ModuleSet
import java.nio.file.Path

// region Validation Error Types

sealed interface ValidationError {
  val context: String
}

// region File Diff (implements ValidationError)

/**
 * Type of file change detected.
 */
enum class FileChangeType {
  /** File will be created */
  CREATE,
  /** File content will be modified */
  MODIFY,
  /** File will be deleted */
  DELETE,
}

/**
 * Represents a file difference detected during generation.
 * Implements [ValidationError] so diffs can be handled uniformly with other validation issues.
 *
 * Field semantics match [com.intellij.platform.testFramework.core.FileComparisonFailedError]:
 * - [expectedContent] = what we WANT (generated/new content)
 * - [actualContent] = what we HAVE (current content on disk)
 */
data class FileDiff(
  override val context: String,
  @JvmField val path: Path,
  /** The generated/desired content (what the file should contain) */
  @JvmField val expectedContent: String,
  /** The current content on disk (what the file actually contains) */
  @JvmField val actualContent: String,
  @JvmField val changeType: FileChangeType,
) : ValidationError

// endregion

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

/**
 * Error when a plugin has dependencies that cannot be resolved in any product that bundles it.
 */
data class PluginDependencyError(
  override val context: String,
  /** The plugin module name */
  @JvmField val pluginName: String,
  /** Missing dependencies -> products where they're missing */
  @JvmField val missingDependencies: Map<String, Set<String>>,
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
  /** Products where this module's plugin is bundled (ships with the product) */
  @JvmField val bundledInProducts: Set<String> = emptySet(),
  /** Products where this module's plugin is compatible (installable from marketplace) but not bundled */
  @JvmField val compatibleWithProducts: Set<String> = emptySet(),
)

// endregion

// region Product Module Index

/**
 * Index of modules in a product - used for validation of module availability.
 * Contains module names plus metadata for error message formatting.
 */
internal data class ProductModuleIndex(
  @JvmField val productName: String,
  @JvmField val allModules: Set<String>,
  @JvmField val referencedModuleSets: Set<String>,
  @JvmField val moduleLoadings: Map<String, ModuleLoadingRuleValue?>,
  /** Map module name -> bundled plugin name that contributed it */
  @JvmField val moduleToSourcePlugin: Map<String, String> = emptyMap(),
  /** Map module name -> module set name that contributed it */
  @JvmField val moduleToSourceModuleSet: Map<String, String> = emptyMap(),
)

// endregion


