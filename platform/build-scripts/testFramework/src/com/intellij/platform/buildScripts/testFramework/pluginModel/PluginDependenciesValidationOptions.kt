// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework.pluginModel

import com.intellij.ide.plugins.ContentModuleDescriptor
import com.intellij.ide.plugins.DependsSubDescriptor
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginMainDescriptor
import com.intellij.ide.plugins.shortLogDescription
import com.intellij.openapi.extensions.PluginId

/**
 * Options for [PluginDependenciesValidator].
 */
data class PluginDependenciesValidationOptions(
  /**
   * List of dependencies to temporarily ignore during validation.
   * Each entry suppresses the check that [MissingCompileDep.toModule] is reachable from
   * [MissingCompileDep.fromModule]'s classloader at runtime.
   * Wildcards are supported: `*` matches any name; a trailing `*` matches any suffix.
   *
   * **This list is a temporary solution.** Every entry MUST reference a YouTrack issue via [MissingCompileDep.issueId].
   * The team is working on auto-generating XML descriptor dependencies from `.iml` files; each item
   * here represents tracked debt that must be resolved before that migration is complete.
   */
  val missingCompileDeps: List<MissingCompileDep> = emptyList(),

  /**
   * Stub plugins to temporarily inject into the plugin set to satisfy runtime dependency declarations absent from the distribution.
   *
   * Use an empty list to satisfy a `<plugin id="…"/>` dependency.
   * Use a non-empty list to also satisfy `<module name="…"/>` dependencies.
   *
   * **This list is a temporary solution.** Every entry MUST reference a YouTrack issue via [MissingRuntimeDep.issueId].
   * Use this only when the real plugin is intentionally absent from the distribution and there is
   * a tracked plan to either include it or remove the dependency.
   */
  val missingRuntimeDeps: List<MissingRuntimeDep> = emptyList(),

  /**
   * Relative paths to XML files which are located in libraries and included in plugin descriptors via `<xi:include>`.
   */
  val pathsIncludedFromLibrariesViaXiInclude: Set<String> = emptySet(),

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
   * Specifies variants of plugins enabled via a system property.
   * Such variants won't be checked, but this information will be used to determine modules which are loaded by separate classloaders in such a variant, and therefore shouldn't be
   * treated as loaded by the plugin descriptor's classloader.
   */
  val pluginVariantsWithDynamicIncludes: List<PluginVariantWithDynamicIncludes> = emptyList(),

  /**
   * Specifies plugin ids which should be excluded from the validation.
   */
  val pluginsToIgnore: Set<String> = emptySet(),

  /**
   * Checks that each used extension point is declared in the same runtime dependency tree.
   */
  val checkExtensionPointDependencies: Boolean = true,

  /**
   * Please don't add new suppressions without a good reason
   */
  val extensionPointDependencyViolationsToIgnore: Set<ExtensionPointDependencyViolation> = emptySet(),
)

/**
 * Defines a variant of a plugin with a custom value of system property used in `includeUnless`/`includeIf` directives.
 */
class PluginVariantWithDynamicIncludes(
  val pluginId: PluginId,
  val systemPropertyKey: String,
  val systemPropertyValue: Boolean,
)

/**
 * A suppressed missing compile-time dependency. Every instance MUST reference a YouTrack issue.
 *
 * @param fromModule the module that has the compile dependency (wildcards `*` supported).
 * @param toModule the dependency module absent from the distribution (wildcards `*` supported).
 * @param issueId the YouTrack issue tracking the root cause and the planned fix, e.g. `"IDEA-123456"`.
 */
data class MissingCompileDep(
  val fromModule: String,
  val toModule: String,
  val issueId: String,
)

/**
 * An injected stub plugin to satisfy a runtime dependency absent from the distribution.
 * Every instance MUST reference a YouTrack issue.
 *
 * @param pluginId the plugin ID of the stub to inject.
 * @param moduleIds content module IDs hosted by the stub; use an empty list to satisfy only `<plugin id="…"/>` deps.
 * @param issueId the YouTrack issue tracking the root cause and the planned fix, e.g. `"IDEA-123456"`.
 */
data class MissingRuntimeDep(
  val pluginId: String,
  val moduleIds: List<String> = emptyList(),
  val issueId: String,
)

/**
 * @param moduleName the name of the JPS module that contains a descriptor with a violation
 * @param extensionPointName fully-qualified name of the extension point
 */
data class ExtensionPointDependencyViolation(
  val moduleName: String,
  val extensionPointName: String,
) {
  constructor(
    descriptor: IdeaPluginDescriptorImpl,
    extensionPointName: String,
  ) : this(
    when (descriptor) {
      is PluginMainDescriptor -> descriptor.pluginId.idString
      is ContentModuleDescriptor -> descriptor.moduleId.name
      is DependsSubDescriptor -> descriptor.shortLogDescription
    },
    extensionPointName,
  )

  override fun toString(): String = """ExtensionPointDependencyViolation("$moduleName", "$extensionPointName")""" // for easy copy-paste
}