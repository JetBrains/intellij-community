// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.productLayout

import com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRule

// Constants for magic strings used throughout the generator
internal const val ADDITIONAL_MODULES_BLOCK = "additional"
internal const val JETBRAINS_NAMESPACE = "jetbrains"
internal const val GENERATOR_SUFFIX = ".main()"
internal const val MODULE_SET_PREFIX = "intellij.moduleSets."

/**
 * Type-safe wrapper for module set names.
 * Prevents accidental mixing of module set names with other string types.
 */
@JvmInline
value class ModuleSetName(val value: String) {
  override fun toString(): String = value
}

/**
 * Represents a single content block in a product plugin.xml.
 * Each block corresponds to a module set or additional modules section.
 */
data class ContentBlock(
  /** Source identifier for the block (e.g., "essential", "vcs", "additional") */
  @JvmField val source: String,
  /** List of modules with their effective loading modes */
  @JvmField val modules: List<ModuleWithLoading>,
)

/**
 * A module with its effective loading mode after applying overrides and exclusions.
 */
data class ModuleWithLoading(
  /** Module name */
  @JvmField val name: String,
  /** Effective loading mode (null means default/no attribute) */
  @JvmField val loading: ModuleLoadingRule?,
  /** Whether to include dependencies of this module */
  @JvmField val includeDependencies: Boolean = false,
)

/**
 * Result of building product content XML.
 * Contains the generated XML string, content blocks, and module-to-set chain mapping.
 */
data class ProductContentBuildResult(
  /** Generated XML content as string */
  @JvmField val xml: String,
  /** List of content blocks generated from the spec */
  @JvmField val contentBlocks: List<ContentBlock>,
  /** Mapping from module name to its module set chain as list (e.g., ["parent", "child"]) */
  @JvmField val moduleToSetChainMapping: Map<String, List<String>>,
  /** Mapping from module name to includeDependencies flag */
  @JvmField val moduleToIncludeDependenciesMapping: Map<String, Boolean>,
)

/**
 * Result of building module set XML.
 * Contains the XML string and count of direct modules (excluding nested).
 */
internal data class ModuleSetBuildResult(
  @JvmField val xml: String,
  @JvmField val directModuleCount: Int,
)