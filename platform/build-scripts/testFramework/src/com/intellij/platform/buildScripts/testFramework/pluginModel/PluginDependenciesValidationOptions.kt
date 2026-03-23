// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework.pluginModel

import com.intellij.openapi.extensions.PluginId

/**
 * Options for [PluginDependenciesValidator].
 */
class PluginDependenciesValidationOptions(
  /**
   * List of a module name and a name of a missing dependency in this module which shouldn't be reported by the validator.
   * This can be used to temporarily suppress some errors while they are being fixed in plugins or while a false-positive is being fixed in the validator. 
   */
  val missingDependenciesToIgnore: List<Pair<String, String>>,

  /**
   * Prefixes of plugin loading error messages which shouldn't be reported by the validator.
   */
  val pluginErrorPrefixesToIgnore: List<String>,

  /**
   * Relative paths to XML files which are located in libraries and included in plugin descriptors via `<xi:include>`.
   */
  val pathsIncludedFromLibrariesViaXiInclude: Set<String>,

  /**
   * Specifies the minimum number of modules which should be checked by the validator.
   * This is used to ensure that the validator won't stop checking many modules due to some mistake in the validation code.
   */
  val minimumNumberOfModulesToBeChecked: Int,

  /**
   * Set of pairs corresponding to dependencies used for compilation only, the validator will ignore such dependencies if they have 'provided' scope.
   * The second element of a pair is the name of the target module.
   * The first element of a pair is either the name of the module that has the dependency, or `*` if dependencies on the target module in all modules should be treated as compile-only.
   *
   * It's supposed that elements from the target module are either not referenced from class-files at all, or the source module is used outside the IDE process where these classes
   * are present in the classpath.
   */
  val compileOnlyDependencies: List<Pair<String, String>> = compileOnlyDependenciesInCommunity,

  /**
   * Set to `true` to enable reporting problems if target module is not included in distribution.
   * This flag is temporarily added to enable this check for some tests only, it'll be removed when the check is enabled for all tests.
   */
  val reportProblemIfTargetModuleIsNotIncludedInDistribution: Boolean = false,

  /**
   * Specifies variants of plugins enabled via a system property.
   * Such variants won't be checked, but this information will be used to determine modules which are loaded by separate classloaders in such a variant, and therefore shouldn't be
   * treated as loaded by the plugin descriptor's classloader.
   */
  val pluginVariantsWithDynamicIncludes: List<PluginVariantWithDynamicIncludes> = emptyList(),

  /**
   * Specifies plugin ids which should be excluded from the validation.
   */
  val pluginsToIgnore: List<PluginId> = emptyList(),
)

/**
 * Defines a variant of a plugin with a custom value of system property used in `includeUnless`/`includeIf` directives.
 */
class PluginVariantWithDynamicIncludes(
  val pluginId: PluginId,
  val systemPropertyKey: String,
  val systemPropertyValue: Boolean,
)