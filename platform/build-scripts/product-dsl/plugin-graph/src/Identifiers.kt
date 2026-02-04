// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.pluginGraph

import kotlinx.serialization.Serializable

/**
 * Type-safe wrapper for plugin XML identifier.
 *
 * A PluginId is the `<id>` element from plugin.xml (e.g., "com.intellij.css", "PythonCore"),
 * or a plugin alias declared via `<module value="alias.name"/>` (e.g., "com.intellij.modules.java").
 *
 * **Used in:**
 * - `<plugin id="..."/>` dependency declarations in module descriptors
 * - `<depends>plugin.id</depends>` in plugin.xml (legacy)
 * - Plugin alias references (e.g., IDE capability markers like "com.intellij.modules.ruby-capable")
 *
 * **NOT the same as [ContentModuleName]** - a plugin may have a different ID than its main module name.
 * For example, plugin ID "PythonCore" vs. module name "intellij.python.community.plugin".
 *
 * See `docs/IntelliJ-Platform/4_man/Plugin-Model/Plugin-Model-v1-v2.md` for detailed explanation.
 */
@Serializable
@JvmInline
value class PluginId(val value: String) : Comparable<PluginId> {
  override fun compareTo(other: PluginId): Int = value.compareTo(other.value)
  override fun toString(): String = value
}

/**
 * Type-safe wrapper for JPS module name.
 *
 * A ModuleName is the module identifier from .iml files (e.g., "intellij.platform.core",
 * "intellij.css.plugin", "intellij.python.community.plugin").
 *
 * **Used in:**
 * - `<module name="..."/>` dependency declarations in module descriptors
 * - Module set definitions and content module references
 * - IML orderEntry references for inter-module dependencies
 *
 * **NOT the same as [PluginId]** - modules are JPS build units, while plugins are runtime units.
 * A plugin content module's descriptor file is named `<module.name>.xml` (e.g., `intellij.css.plugin.xml`).
 *
 * **Test Descriptors:**
 * Test descriptors use a `._test` suffix (e.g., `intellij.foo._test`), which is a synthetic name.
 * The actual JPS module is `intellij.foo` (without the suffix). Use [isTestDescriptor] and
 * [baseModuleName] to handle this convention.
 *
 * See `docs/IntelliJ-Platform/4_man/Plugin-Model/Plugin-Model-v1-v2.md` for detailed explanation.
 */
@Serializable
@JvmInline
value class ContentModuleName(val value: String) : Comparable<ContentModuleName> {
  override fun compareTo(other: ContentModuleName): Int = value.compareTo(other.value)
  override fun toString(): String = value
}

/**
 * Type-safe wrapper for build target name.
 *
 * A TargetName identifies a buildable unit - currently a JPS module that represents
 * a plugin or standalone build target. As the codebase migrates from JPS to Bazel,
 * this will map to Bazel target labels.
 *
 * **Used in:**
 * - Plugin identification (the main module/target of a plugin)
 * - Build target references in validation
 * - Test plugin detection
 *
 * **NOT the same as [ContentModuleName]** - TargetName identifies build targets (plugins),
 * while ModuleName identifies content modules within the plugin model.
 *
 * **Semantic distinction:**
 * - [TargetName] = Project structure model (what to build)
 * - [Content ModuleName] = Plugin model (content modules declared in plugin.xml)
 *
 * Example: Plugin `intellij.platform.testFramework` (TargetName) contains content modules
 * `intellij.platform.testFramework.core`, `intellij.platform.testFramework.impl` (ModuleName).
 */
@Serializable
@JvmInline
value class TargetName(val value: String) : Comparable<TargetName> {
  override fun compareTo(other: TargetName): Int = value.compareTo(other.value)
  override fun toString(): String = value
}

/** Suffix used for test descriptor synthetic module names */
const val TEST_DESCRIPTOR_SUFFIX: String = "._test"

/** Returns true if this is a test descriptor synthetic name (ends with `._test`) */
fun ContentModuleName.isTestDescriptor(): Boolean = value.endsWith(TEST_DESCRIPTOR_SUFFIX)

/**
 * Returns the base JPS module name.
 *
 * Handles two conventions:
 * 1. Test descriptors: `intellij.foo._test` → `intellij.foo` (removes `._test` suffix)
 * 2. Slash-notation: `intellij.restClient/intelliLang` → `intellij.restClient` (parent plugin module)
 *
 * For regular modules, returns the same name unchanged.
 */
fun ContentModuleName.baseModuleName(): ContentModuleName {
  return when {
    isSlashNotation() -> ContentModuleName(value.substringBefore('/'))
    isTestDescriptor() -> ContentModuleName(value.removeSuffix(TEST_DESCRIPTOR_SUFFIX))
    else -> this
  }
}

// region Slash-Notation Module Support
//
// Slash notation (e.g., "intellij.restClient/intelliLang") represents virtual content modules:
// - No separate JPS module - descriptor is in parent plugin's resource root
// - Descriptor file naming: parent.subModule.xml (dots, not slashes)
// - Example: intellij.restClient/intelliLang → intellij.restClient.intelliLang.xml

/**
 * Returns true if this is a slash-notation module (e.g., "intellij.restClient/intelliLang").
 *
 * Slash-notation modules are virtual content modules without a separate JPS module.
 * Their descriptor file is located in the parent plugin's resource root.
 */
fun ContentModuleName.isSlashNotation(): Boolean = '/' in value

/**
 * Returns the parent plugin name for slash-notation modules.
 *
 * For "intellij.restClient/intelliLang", returns "intellij.restClient".
 * For regular modules, returns null.
 */
fun ContentModuleName.parentPluginName(): String? = if (isSlashNotation()) value.substringBefore('/') else null

/**
 * Converts module name to descriptor file name.
 *
 * For slash-notation "intellij.restClient/intelliLang", returns "intellij.restClient.intelliLang.xml".
 * For regular modules "intellij.foo", returns "intellij.foo.xml".
 */
fun ContentModuleName.toDescriptorFileName(): String {
  return if (isSlashNotation()) {
    "${value.replace('/', '.')}.xml"
  }
  else {
    "$value.xml"
  }
}

// endregion

// region Target Dependency Scope

/**
 * Dependency scope for target-to-target (JPS module) dependencies.
 *
 * Mirrors `JpsJavaDependencyScope` from jps-model to avoid dependency on that module.
 * The plugin-graph module should remain lightweight without JPS dependencies.
 *
 * **Used in:**
 * - `EDGE_TARGET_DEPENDS_ON` packed edge entries to track dependency scope
 * - Filtering dependencies (e.g., skip TEST scope for production XML)
 *
 * See `org.jetbrains.jps.model.java.JpsJavaDependencyScope`.
 */
@Serializable
enum class TargetDependencyScope {
  COMPILE,
  TEST,
  RUNTIME,
  PROVIDED,
  ;

  companion object {
    private val entries = enumValues<TargetDependencyScope>()

    fun fromOrdinal(ordinal: Int): TargetDependencyScope = entries[ordinal]
  }
}

// endregion
