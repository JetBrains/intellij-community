// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.deps

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.PluginId
import com.intellij.platform.pluginGraph.TargetName
import org.jetbrains.intellij.build.productLayout.TestPluginSpec
import org.jetbrains.intellij.build.productLayout.model.error.DslTestPluginDependencySource
import org.jetbrains.intellij.build.productLayout.model.error.DslTestPluginOwner
import org.jetbrains.intellij.build.productLayout.model.error.UnsuppressedPipelineError
import org.jetbrains.intellij.build.productLayout.stats.SuppressionUsage
import java.nio.file.Path

/**
 * Dependency plan for a single content module descriptor (moduleName.xml).
 */
internal data class ContentModuleDependencyPlan(
  val contentModuleName: ContentModuleName,
  @JvmField val descriptorPath: Path,
  @JvmField val descriptorContent: String,
  /** Module dependencies to write into XML. */
  @JvmField val moduleDependencies: List<ContentModuleName>,
  /** Plugin dependencies to write into XML. */
  @JvmField val pluginDependencies: List<PluginId>,
  /** Test dependencies for graph (superset of [moduleDependencies]). */
  @JvmField val testDependencies: List<ContentModuleName>,
  /** Existing module deps already in XML. */
  @JvmField val existingXmlModuleDependencies: Set<ContentModuleName>,
  /** Existing plugin deps already in XML. */
  @JvmField val existingXmlPluginDependencies: Set<PluginId>,
  /** Effective plugin deps written to XML (auto + preserved). */
  @JvmField val writtenPluginDependencies: List<PluginId>,
  /** All plugin deps inferred from JPS (before suppressions). */
  @JvmField val allJpsPluginDependencies: Set<PluginId>,
  /** Suppressed module deps (used to preserve existing manual entries). */
  @JvmField val suppressedModules: Set<ContentModuleName>,
  /** Suppressed plugin deps (used to preserve existing manual entries). */
  @JvmField val suppressedPlugins: Set<PluginId>,
  /** Suppression usages recorded during planning. */
  @JvmField val suppressionUsages: List<SuppressionUsage>,
  /** Suppressible error when descriptor root is non-standard. */
  @JvmField val suppressibleError: UnsuppressedPipelineError? = null,
)

internal data class ContentModuleDependencyPlanOutput(
  @JvmField val plans: List<ContentModuleDependencyPlan>,
) {
  @JvmField val plansByModule: Map<ContentModuleName, ContentModuleDependencyPlan> =
    plans.associateBy { it.contentModuleName }
}

/**
 * Dependency plan for a single plugin.xml file.
 */
internal data class PluginDependencyPlan(
  val pluginContentModuleName: ContentModuleName,
  @JvmField val pluginXmlPath: Path,
  @JvmField val pluginXmlContent: String,
  /** Module dependencies to write into plugin.xml. */
  @JvmField val moduleDependencies: List<ContentModuleName>,
  /** Plugin dependencies to write into plugin.xml. */
  @JvmField val pluginDependencies: List<PluginId>,
  /** Legacy plugin dependencies (<depends>) for semantic comparison. */
  @JvmField val legacyPluginDependencies: List<PluginId>,
  /** Deps already present via xi:include. */
  @JvmField val xiIncludeModuleDeps: Set<ContentModuleName>,
  @JvmField val xiIncludePluginDeps: Set<PluginId>,
  /** Existing deps in main plugin.xml. */
  @JvmField val existingXmlModuleDependencies: Set<ContentModuleName>,
  @JvmField val existingXmlPluginDependencies: Set<PluginId>,
  /** Existing deps to preserve during update (manual or filtered). */
  @JvmField val preserveExistingModuleDependencies: Set<ContentModuleName>,
  @JvmField val preserveExistingPluginDependencies: Set<PluginId>,
  /** Suppression usages recorded during planning. */
  @JvmField val suppressionUsages: List<SuppressionUsage>,
  /** Plugin IDs declared both in legacy and modern formats. */
  @JvmField val duplicateDeclarationPluginIds: Set<PluginId> = emptySet(),
)

internal data class PluginDependencyPlanOutput(
  @JvmField val plans: List<PluginDependencyPlan>,
)

/**
 * Dependency plan for a single DSL-defined test plugin.
 */
internal data class TestPluginDependencyPlan(
  @JvmField val spec: TestPluginSpec,
  @JvmField val productName: String,
  @JvmField val productClass: String,
  /** Plugin dependencies to declare in test plugin.xml */
  @JvmField val pluginDependencies: List<PluginId>,
  /** Module dependencies to declare in test plugin.xml */
  @JvmField val moduleDependencies: List<ContentModuleName>,
  /** Plugin dependencies required by content modules (before allowed-missing filtering) */
  @JvmField val requiredByPlugin: Map<PluginId, Set<ContentModuleName>>,
  /** Unresolvable dependencies discovered during planning */
  @JvmField val unresolvedDependencies: List<TestPluginUnresolvedDependency>,
)

internal data class TestPluginDependencyPlanOutput(
  @JvmField val plans: List<TestPluginDependencyPlan>,
) {
  @JvmField val plansByPluginId: Map<PluginId, TestPluginDependencyPlan> =
    plans.associateBy { it.spec.pluginId }
}

/**
 * Unresolvable dependency captured during planning for later validation.
 */
internal sealed interface TestPluginUnresolvedDependency {
  /**
   * Content module dependency owned by production plugin(s) that are not resolvable for this test plugin.
   */
  data class ContentModule(
    val dependencyId: ContentModuleName,
    val owningPlugins: Set<DslTestPluginOwner>,
    val dependencySource: DslTestPluginDependencySource? = null,
  ) : TestPluginUnresolvedDependency

  /**
   * Plugin dependency that is not resolvable for this test plugin.
   */
  data class Plugin(
    val dependencyId: PluginId,
    val dependencyTargetNames: Set<TargetName>,
  ) : TestPluginUnresolvedDependency
}
