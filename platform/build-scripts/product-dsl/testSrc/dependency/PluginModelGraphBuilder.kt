// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.productLayout.dependency

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.EDGE_ALLOWS_MISSING
import com.intellij.platform.pluginGraph.EDGE_BACKED_BY
import com.intellij.platform.pluginGraph.EDGE_BUNDLES
import com.intellij.platform.pluginGraph.EDGE_BUNDLES_TEST
import com.intellij.platform.pluginGraph.EDGE_CONTAINS_CONTENT
import com.intellij.platform.pluginGraph.EDGE_CONTAINS_CONTENT_TEST
import com.intellij.platform.pluginGraph.EDGE_CONTAINS_MODULE
import com.intellij.platform.pluginGraph.EDGE_CONTENT_MODULE_DEPENDS_ON
import com.intellij.platform.pluginGraph.EDGE_CONTENT_MODULE_DEPENDS_ON_TEST
import com.intellij.platform.pluginGraph.EDGE_INCLUDES_MODULE_SET
import com.intellij.platform.pluginGraph.EDGE_MAIN_TARGET
import com.intellij.platform.pluginGraph.EDGE_NESTED_SET
import com.intellij.platform.pluginGraph.EDGE_PLUGIN_XML_DEPENDS_ON_CONTENT_MODULE
import com.intellij.platform.pluginGraph.EDGE_TARGET_DEPENDS_ON
import com.intellij.platform.pluginGraph.PLUGIN_DEP_LEGACY_MASK
import com.intellij.platform.pluginGraph.PLUGIN_DEP_MODERN_MASK
import com.intellij.platform.pluginGraph.PluginGraph
import com.intellij.platform.pluginGraph.PluginId
import com.intellij.platform.pluginGraph.TargetDependencyScope
import com.intellij.platform.pluginGraph.TargetName
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue
import org.jetbrains.intellij.build.productLayout.graph.PluginGraphBuilder as ProductionGraphBuilder

/**
 * DSL marker for graph builder to prevent scope leakage.
 */
@DslMarker
internal annotation class GraphBuilderDsl

/**
 * Fluent builder for [PluginGraph] in tests.
 *
 * Mirrors production graph structure with a simple DSL:
 * ```kotlin
 * val graph = pluginGraph {
 *   product("IDEA") {
 *     bundlesPlugin("intellij.platform.vcs")
 *     bundlesTestPlugin("intellij.rdct.tests")
 *     includesModuleSet("ide.common")
 *   }
 *   plugin("intellij.platform.vcs") {
 *     pluginId("com.intellij.vcs")
 *     content("intellij.platform.vcs.impl", loading = REQUIRED)
 *   }
 *   testPlugin("intellij.rdct.tests") {
 *     content("intellij.libraries.assertj.core")
 *   }
 *   moduleSet("ide.common") {
 *     module("intellij.platform.ide.core")
 *   }
 * }
 * ```
 *
 * The built graph uses the same vertex/edge labels and properties as production code,
 * ensuring tests validate identical graph traversals.
 */
/**
 * Fluent test DSL builder that delegates to production [ProductionGraphBuilder].
 *
 * Uses production builder for proper upsert semantics.
 */
@GraphBuilderDsl
internal class TestPluginGraphBuilder {
  private val delegate = ProductionGraphBuilder()

  internal fun getOrCreateTarget(name: TargetName): Int = delegate.addTarget(name)

  internal fun getOrCreatePlugin(name: TargetName, isTest: Boolean = false, isDslDefined: Boolean = false): Int {
    return delegate.addPlugin(name, isTest, isDslDefined, pluginId = PluginId(name.value))
  }

  internal fun getOrCreateModule(name: ContentModuleName): Int = delegate.markContentModuleHasDescriptor(name)

  internal fun getOrCreateProduct(name: String): Int = delegate.addProduct(name)

  internal fun getOrCreateModuleSet(name: String, selfContained: Boolean = false): Int = delegate.addModuleSet(name, selfContained)

  internal fun setPluginId(pluginId: Int, id: String) {
    delegate.setPluginId(pluginId, id)
  }

  internal fun addEdge(source: Int, target: Int, edgeType: Int) {
    delegate.addEdge(source, target, edgeType)
  }

  internal fun addTargetDependencyEdge(source: Int, target: Int, scope: TargetDependencyScope?) {
    delegate.addTargetDependencyEdge(source, target, scope)
  }

  internal fun addEdgeWithLoadingMode(source: Int, target: Int, edgeType: Int, loadingMode: ModuleLoadingRuleValue) {
    delegate.addEdgeWithLoadingMode(source, target, edgeType, loadingMode)
  }

  internal fun addPluginDependencyEdge(
    sourcePluginId: Int,
    depId: PluginId,
    isOptional: Boolean,
    formatMask: Int,
    hasConfigFile: Boolean = false,
  ) {
    delegate.addPluginDependencyEdgeForTest(sourcePluginId, depId, isOptional, formatMask, hasConfigFile)
  }

  internal suspend fun markDescriptorModules(descriptorCache: ModuleDescriptorCache) {
    delegate.markDescriptorModules(descriptorCache)
  }

  /**
   * Declare a product with optional bundling configuration.
   */
  fun product(name: String, block: GraphProductBuilder.() -> Unit = {}) {
    val productId = getOrCreateProduct(name)
    GraphProductBuilder(productId, this).apply(block)
  }

  /**
   * Declare a production plugin with optional content modules.
   */
  fun plugin(name: String, block: GraphPluginBuilder.() -> Unit = {}) {
    val pluginId = getOrCreatePlugin(TargetName(name), isTest = false)
    GraphPluginBuilder(pluginId, this).apply(block)
  }

  /**
   * Declare a test plugin with optional content modules.
   * Test plugins are marked as DSL-defined (isDslDefined=true).
   */
  fun testPlugin(name: String, block: GraphPluginBuilder.() -> Unit = {}) {
    val pluginId = getOrCreatePlugin(TargetName(name), isTest = true, isDslDefined = true)
    GraphPluginBuilder(pluginId, this).apply(block)
  }

  /**
   * Declare a module set with modules.
   *
   * @param selfContained If true, marks this set as self-contained (must be resolvable in isolation)
   */
  fun moduleSet(name: String, selfContained: Boolean = false, block: GraphModuleSetBuilder.() -> Unit = {}) {
    val moduleSetId = getOrCreateModuleSet(name, selfContained)
    GraphModuleSetBuilder(moduleSetId, this).apply(block)
  }

  /**
   * Declare a target (JPS/Bazel module) with optional dependencies.
   */
  fun target(name: String, block: GraphTargetBuilder.() -> Unit = {}) {
    val targetId = getOrCreateTarget(TargetName(name))
    GraphTargetBuilder(targetId, this).apply(block)
  }

  /**
   * Link a module to its backing target and optionally set up target dependencies.
   *
   * Convenience method that creates:
   * - Module vertex (if not exists)
   * - Target vertex (if not exists)
   * - Module --backedBy--> Target edge
   * - Target dependencies if [deps] is provided
   *
   * **Important:** The dependency MODULE node must already exist from a prior `moduleWithDeps` call
   * for that module. This function only creates TARGET nodes for dependencies.
   */
  fun moduleWithDeps(name: String, vararg deps: String) {
    val moduleId = getOrCreateModule(ContentModuleName(name))
    val targetId = getOrCreateTarget(TargetName(name))
    addEdge(moduleId, targetId, EDGE_BACKED_BY)
    for (dep in deps) {
      val depTargetId = getOrCreateTarget(TargetName(dep))
      addTargetDependencyEdge(targetId, depTargetId, null)
    }
  }

  /**
   * Link a module to its backing target with JPS dependencies that have scope.
   *
   * Similar to [moduleWithDeps] but packs JPS dependency scope into the target edge entries,
   * enabling scope-aware filtering in `computeImplicitDeps`.
   *
   * @param name Module/target name
   * @param deps List of pairs: (dependency name, scope name like "COMPILE", "TEST", "RUNTIME")
   */
  fun moduleWithScopedDeps(name: String, vararg deps: Pair<String, String>) {
    val moduleId = getOrCreateModule(ContentModuleName(name))
    val targetId = getOrCreateTarget(TargetName(name))
    addEdge(moduleId, targetId, EDGE_BACKED_BY)
    for ((depName, scope) in deps) {
      val depTargetId = getOrCreateTarget(TargetName(depName))
      addEdgeWithScope(targetId, depTargetId, EDGE_TARGET_DEPENDS_ON, scope)
    }
  }

  internal fun addEdgeWithScope(source: Int, target: Int, edgeType: Int, scope: String) {
    require(edgeType == EDGE_TARGET_DEPENDS_ON) { "Scope packing is only supported for EDGE_TARGET_DEPENDS_ON" }
    delegate.addTargetDependencyEdge(source, target, TargetDependencyScope.valueOf(scope))
  }

  /**
   * Add content module dependencies between modules (Module --moduleDependsOn--> Module).
   *
   * These edges represent runtime dependencies from module descriptor XML
   * (`<dependencies><module name="..."/>`), as computed by ContentModuleDependencyPlanner.
   *
   * Use this to set up graph state for testing validation rules that traverse
   * plugin model dependencies instead of JPS dependencies.
   */
  fun linkContentModuleDeps(moduleName: String, vararg deps: String) {
    val moduleId = getOrCreateModule(ContentModuleName(moduleName))
    for (dep in deps) {
      val depModuleId = getOrCreateModule(ContentModuleName(dep))
      addEdge(moduleId, depModuleId, EDGE_CONTENT_MODULE_DEPENDS_ON)
    }
  }

  /**
   * Add content module TEST dependencies between modules (Module --moduleDependsOnTest--> Module).
   *
   * These edges represent TEST scope JPS dependencies from IML files, which should only be
   * used for test plugin validation (not production). Production validation uses
   * [linkContentModuleDeps] edges only.
   *
   * Use this in tests that verify test dependencies don't leak into production validation.
   */
  fun linkContentModuleTestDeps(moduleName: String, vararg deps: String) {
    val moduleId = getOrCreateModule(ContentModuleName(moduleName))
    for (dep in deps) {
      val depModuleId = getOrCreateModule(ContentModuleName(dep))
      addEdge(moduleId, depModuleId, EDGE_CONTENT_MODULE_DEPENDS_ON_TEST)
    }
  }

  /**
   * Link a plugin to its main target (Plugin --mainTarget--> Target).
   *
   * This mirrors what ModelBuildingStage Phase 5 does in production. The main target
   * is the JPS/Bazel module that backs the plugin.
   *
   * Call this after creating the target with [target] to set up the EDGE_MAIN_TARGET edge.
   * If plugin doesn't exist yet, creates it as a non-test plugin.
   */
  fun linkPluginMainTarget(pluginName: String) {
    val pluginId = getOrCreatePlugin(TargetName(pluginName), isTest = false)
    val targetId = getOrCreateTarget(TargetName(pluginName))
    addEdge(pluginId, targetId, EDGE_MAIN_TARGET)
  }

  /**
   * Build the final [PluginGraph].
   */
  fun build(): PluginGraph = delegate.build()
}

/**
 * Builder for product vertex configuration.
 */
@GraphBuilderDsl
internal class GraphProductBuilder(
  private val productId: Int,
  private val builder: TestPluginGraphBuilder,
) {
  /**
   * Bundle a production plugin in this product.
   */
  fun bundlesPlugin(name: String) {
    val pluginId = builder.getOrCreatePlugin(TargetName(name), isTest = false)
    builder.addEdge(productId, pluginId, EDGE_BUNDLES)
  }

  /**
   * Bundle a test plugin in this product.
   */
  fun bundlesTestPlugin(name: String) {
    val pluginId = builder.getOrCreatePlugin(TargetName(name), isTest = true)
    builder.addEdge(productId, pluginId, EDGE_BUNDLES_TEST)
  }

  /**
   * Include a module set in this product.
   */
  fun includesModuleSet(name: String) {
    val moduleSetId = builder.getOrCreateModuleSet(name)
    builder.addEdge(productId, moduleSetId, EDGE_INCLUDES_MODULE_SET)
  }

  /**
   * Allow a module to be missing in validation.
   */
  fun allowsMissing(moduleName: String) {
    val moduleId = builder.getOrCreateModule(ContentModuleName(moduleName))
    builder.addEdge(productId, moduleId, EDGE_ALLOWS_MISSING)
  }
}

/**
 * Builder for plugin vertex configuration.
 */
@GraphBuilderDsl
internal class GraphPluginBuilder(
  private val pluginId: Int,
  private val builder: TestPluginGraphBuilder,
) {
  /**
   * Set the plugin ID.
   */
  fun pluginId(id: String) {
    builder.setPluginId(pluginId, id)
  }

  /**
   * Set the plugin ID from [PluginId].
   */
  fun pluginId(id: PluginId) {
    builder.setPluginId(pluginId, id.value)
  }

  /**
   * Add a content module to this plugin.
   */
  fun content(module: String, loading: ModuleLoadingRuleValue = ModuleLoadingRuleValue.OPTIONAL) {
    val moduleId = builder.getOrCreateModule(ContentModuleName(module))
    builder.addEdgeWithLoadingMode(pluginId, moduleId, EDGE_CONTAINS_CONTENT, loading)
  }

  /**
   * Add a test content module to this plugin.
   */
  fun testContent(module: String, loading: ModuleLoadingRuleValue = ModuleLoadingRuleValue.OPTIONAL) {
    val moduleId = builder.getOrCreateModule(ContentModuleName(module))
    builder.addEdgeWithLoadingMode(pluginId, moduleId, EDGE_CONTAINS_CONTENT_TEST, loading)
  }

  /**
   * Add a modern plugin.xml <dependencies><plugin> dependency to this plugin.
   */
  fun dependsOnPlugin(id: String) {
    builder.addPluginDependencyEdge(pluginId, PluginId(id), isOptional = false, formatMask = PLUGIN_DEP_MODERN_MASK)
  }

  /**
   * Add a legacy plugin.xml <depends> dependency to this plugin.
   */
  fun dependsOnLegacyPlugin(id: String, optional: Boolean = false, hasConfigFile: Boolean = false) {
    builder.addPluginDependencyEdge(
      pluginId,
      PluginId(id),
      isOptional = optional,
      formatMask = PLUGIN_DEP_LEGACY_MASK,
      hasConfigFile = hasConfigFile,
    )
  }

  /**
   * Add a plugin.xml module dependency to this plugin.
   */
  fun dependsOnContentModule(module: String) {
    val moduleId = builder.getOrCreateModule(ContentModuleName(module))
    builder.addEdge(pluginId, moduleId, EDGE_PLUGIN_XML_DEPENDS_ON_CONTENT_MODULE)
  }
}

/**
 * Builder for module set vertex configuration.
 */
@GraphBuilderDsl
internal class GraphModuleSetBuilder(
  private val moduleSetId: Int,
  private val builder: TestPluginGraphBuilder,
) {
  /**
   * Add a module to this module set with optional loading mode.
   */
  fun module(name: String, loading: ModuleLoadingRuleValue = ModuleLoadingRuleValue.OPTIONAL) {
    val moduleId = builder.getOrCreateModule(ContentModuleName(name))
    builder.addEdgeWithLoadingMode(moduleSetId, moduleId, EDGE_CONTAINS_MODULE, loading)
  }

  /**
   * Add a module with backing target and dependencies to this module set.
   *
   * Creates:
   * - ModuleSet --containsModule--> Module
   * - Module --backedBy--> Target
   * - Target --dependsOn--> dep targets (for each dep)
   */
  fun moduleWithDeps(name: String, loading: ModuleLoadingRuleValue = ModuleLoadingRuleValue.OPTIONAL, vararg deps: String) {
    val moduleId = builder.getOrCreateModule(ContentModuleName(name))
    builder.addEdgeWithLoadingMode(moduleSetId, moduleId, EDGE_CONTAINS_MODULE, loading)
    val targetId = builder.getOrCreateTarget(TargetName(name))
    builder.addEdge(moduleId, targetId, EDGE_BACKED_BY)
    for (dep in deps) {
      val depTargetId = builder.getOrCreateTarget(TargetName(dep))
      builder.addTargetDependencyEdge(targetId, depTargetId, null)
    }
  }

  /**
   * Add a nested module set (creates nestedSet edge and containsModule edges for its modules).
   */
  fun nestedSet(name: String, block: GraphModuleSetBuilder.() -> Unit = {}) {
    val nestedId = builder.getOrCreateModuleSet(name)
    builder.addEdge(moduleSetId, nestedId, EDGE_NESTED_SET)
    GraphModuleSetBuilder(nestedId, builder).apply(block)
  }
}

/**
 * Builder for target vertex configuration.
 */
@GraphBuilderDsl
internal class GraphTargetBuilder(
  private val targetId: Int,
  private val builder: TestPluginGraphBuilder,
) {
  /**
   * Add a dependency on another target.
   */
  fun dependsOn(name: String) {
    val depId = builder.getOrCreateTarget(TargetName(name))
    builder.addTargetDependencyEdge(targetId, depId, null)
  }
}

/**
 * Top-level DSL entry point for building a [PluginGraph].
 *
 * Example:
 * ```kotlin
 * val graph = pluginGraph {
 *   product("IDEA") { bundlesPlugin("intellij.vcs") }
 *   plugin("intellij.vcs") { content("intellij.vcs.impl") }
 * }
 * ```
 */
internal inline fun pluginGraph(block: TestPluginGraphBuilder.() -> Unit): PluginGraph = TestPluginGraphBuilder().apply(block).build()

internal suspend fun pluginGraphWithDescriptors(
  descriptorCache: ModuleDescriptorCache,
  block: TestPluginGraphBuilder.() -> Unit,
): PluginGraph {
  val builder = TestPluginGraphBuilder()
  builder.apply(block)
  builder.markDescriptorModules(descriptorCache)
  return builder.build()
}
