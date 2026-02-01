// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/**
 * Plugin dependency resolution and structural validation.
 *
 * Validation queries PluginGraph directly (no parallel resolution map).
 * Availability is computed on demand via content sources in the graph.
 *
 * **Structural validation** ([isStructurallyAllowed], [ResolutionQuery.findStructuralViolations]):
 * Checks loading mode constraints for sibling modules in the same plugin:
 * - EMBEDDED cannot depend on non-EMBEDDED siblings
 * - REQUIRED cannot depend on OPTIONAL/ON_DEMAND siblings
 *
 * **Key function**: [createResolutionQuery] - resolves module availability directly from the graph.
 *
 * @see org.jetbrains.intellij.build.productLayout.validator.PluginContentDependencyValidator for validation using this model
 */
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment", "GrazieStyle")

package org.jetbrains.intellij.build.productLayout.validator.rule

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.ContentModuleNode
import com.intellij.platform.pluginGraph.ContentSource
import com.intellij.platform.pluginGraph.ContentSourceKind
import com.intellij.platform.pluginGraph.EDGE_BUNDLES
import com.intellij.platform.pluginGraph.EDGE_BUNDLES_TEST
import com.intellij.platform.pluginGraph.EDGE_CONTAINS_CONTENT
import com.intellij.platform.pluginGraph.GraphScope
import com.intellij.platform.pluginGraph.ModuleSetNode
import com.intellij.platform.pluginGraph.PluginNode
import com.intellij.platform.pluginGraph.ProductNode
import com.intellij.platform.pluginGraph.TargetName
import com.intellij.platform.pluginGraph.containsEdge
import com.intellij.platform.pluginGraph.contentLoadingMode
import com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRuleValue

// region Test Plugin Detection

/**
 * Detects if a plugin is a test plugin based on its name or content.
 *
 * A plugin is considered a test plugin if:
 * 1. Its name matches test framework patterns (e.g., `*.testFramework`, `*.test.framework`)
 * 2. Any of its content modules are in [testFrameworkContentModules]
 *
 * Name-based detection catches plugins like:
 * - `intellij.platform.testFramework`
 * - `intellij.platform.testFramework.core`
 * - `fleet.backend.testFramework`
 * - `intellij.platform.jps.model.testFramework`
 *
 * @param pluginTarget The build target (plugin) to check
 * @param contentModules Content modules declared in the plugin
 * @param testFrameworkContentModules Known test framework content modules
 */
internal fun isTestPlugin(
  pluginTarget: TargetName,
  contentModules: Set<ContentModuleName>,
  testFrameworkContentModules: Set<ContentModuleName>,
): Boolean {
  // Name-based detection
  if (matchesTestPluginNamePattern(pluginTarget)) {
    return true
  }
  // Content-based detection
  return testFrameworkContentModules.isNotEmpty() && contentModules.any { it in testFrameworkContentModules }
}

/**
 * Checks if a plugin target name matches test plugin patterns.
 *
 * Patterns matched:
 * - `*.testFramework` (e.g., `intellij.platform.testFramework`)
 * - `*.testFramework.*` (e.g., `intellij.platform.testFramework.core`)
 * - `*.test.framework*` (e.g., `some.test.framework.util`)
 */
private fun matchesTestPluginNamePattern(pluginTarget: TargetName): Boolean {
  val name = pluginTarget.value
  return name.endsWith(".testFramework") ||
         name.contains(".testFramework.") ||
         name.contains(".test.framework")
}

// endregion

// region Resolution Query

internal typealias AvailabilityPredicate = ResolutionQuery.(source: ContentSource, productName: String) -> Boolean

internal fun GraphScope.createResolutionQuery(): ResolutionQuery = ResolutionQuery(this)

/**
 * Graph-backed query helper for resolving module availability and structural rules.
 */
internal class ResolutionQuery(private val scope: GraphScope) {
  internal data class SourceScan(
    val hasPluginSource: Boolean,
    val matchesPredicate: Boolean,
  )

  fun scanSources(
    moduleName: ContentModuleName,
    predicate: AvailabilityPredicate,
    productName: String,
    trackPluginSource: Boolean = false,
    includeTestSources: Boolean = false,
  ): SourceScan {
    val moduleNode = scope.contentModule(moduleName) ?: return SourceScan(hasPluginSource = false, matchesPredicate = false)
    var hasPluginSource = false
    var matchesPredicate = false

    forEachContentSource(moduleNode, includeTestSources) { source ->
      if (matchesPredicate && (!trackPluginSource || hasPluginSource)) return@forEachContentSource
      if (trackPluginSource && source.kind == ContentSourceKind.PLUGIN) {
        hasPluginSource = true
      }
      if (!matchesPredicate && predicate(this@ResolutionQuery, source, productName)) {
        matchesPredicate = true
      }
    }

    return SourceScan(hasPluginSource = hasPluginSource, matchesPredicate = matchesPredicate)
  }

  fun findUnresolvedDeps(
    deps: Set<ContentModuleName>,
    predicate: AvailabilityPredicate,
    productName: String,
    allowedMissing: Set<ContentModuleName> = emptySet(),
    includeTestSources: Boolean = false,
  ): Set<ContentModuleName> {
    val unresolved = HashSet<ContentModuleName>(deps.size)
    for (dep in deps) {
      if (dep in allowedMissing) continue
      val scan = scanSources(dep, predicate, productName, includeTestSources = includeTestSources)
      if (!scan.matchesPredicate) {
        unresolved.add(dep)
      }
    }
    return unresolved
  }

  fun findStructuralViolations(
    pluginName: TargetName?,
    loadingMode: ModuleLoadingRuleValue?,
    deps: Set<ContentModuleName>,
  ): Set<ContentModuleName> {
    val violations = HashSet<ContentModuleName>(deps.size)
    for (dep in deps) {
      if (hasStructuralViolation(pluginName, loadingMode, dep)) {
        violations.add(dep)
      }
    }
    return violations
  }

  private fun hasStructuralViolation(
    pluginName: TargetName?,
    dependingLoading: ModuleLoadingRuleValue?,
    dep: ContentModuleName,
  ): Boolean {
    if (pluginName == null) return false
    val moduleNode = scope.contentModule(dep) ?: return false
    val moduleId = moduleNode.id
    var violation = false
    forEachProductionSource(moduleNode) { source ->
      if (violation) return@forEachProductionSource
      if (source.kind != ContentSourceKind.PLUGIN) return@forEachProductionSource
      val plugin = source.plugin()
      if (plugin.name() != pluginName) return@forEachProductionSource
      val dependencyLoading = loadingMode(plugin, moduleId)
      if (!isStructurallyAllowed(dependingLoading, dependencyLoading)) {
        violation = true
      }
    }
    return violation
  }

  private inline fun forEachContentSource(
    moduleNode: ContentModuleNode,
    includeTestSources: Boolean,
    crossinline action: GraphScope.(ContentSource) -> Unit,
  ) {
    scope.run {
      if (includeTestSources) {
        moduleNode.contentSources { source -> action(source) }
      }
      else {
        // Intentionally production-only: test plugin content (EDGE_CONTAINS_CONTENT_TEST) is excluded.
        moduleNode.contentProductionSources { source -> action(source) }
      }
    }
  }

  private inline fun forEachProductionSource(
    moduleNode: ContentModuleNode,
    crossinline action: GraphScope.(ContentSource) -> Unit,
  ) {
    forEachContentSource(moduleNode, includeTestSources = false, action = action)
  }

  fun isModuleSetInProduct(moduleSet: ModuleSetNode, productName: String): Boolean {
    val product = scope.product(productName) ?: return false
    return scope.run { product.includesModuleSetRecursive(moduleSet) }
  }

  fun isPluginBundledInProduct(plugin: PluginNode, productName: String): Boolean {
    val product = scope.product(productName) ?: return false
    return scope.containsEdge(EDGE_BUNDLES, product.id, plugin.id) ||
           scope.containsEdge(EDGE_BUNDLES_TEST, product.id, plugin.id)
  }

  fun pluginName(plugin: PluginNode): TargetName = scope.run { plugin.name() }

  fun productName(product: ProductNode): String = scope.run { product.name() }

  fun isTestPlugin(plugin: PluginNode): Boolean = scope.run { plugin.isTest }

  private fun loadingMode(plugin: PluginNode, moduleId: Int): ModuleLoadingRuleValue? {
    return scope.contentLoadingMode(EDGE_CONTAINS_CONTENT, plugin.id, moduleId)
  }
}

// endregion

// region Layer 1: Structural Validation

/**
 * Check if dependency relationship is structurally allowed.
 *
 * Rules for sibling modules (same plugin):
 * 1. EMBEDDED modules cannot depend on non-EMBEDDED siblings.
 *    Reason: Embedded modules are merged into the main plugin JAR; they cannot depend on modules
 *    that are packaged separately (REQUIRED/OPTIONAL/ON_DEMAND).
 *
 * 2. REQUIRED modules cannot depend on OPTIONAL/ON_DEMAND siblings.
 *    Reason: This effectively makes the optional module required - the plugin won't load
 *    if the optional module's dependencies aren't available, defeating the purpose of optional loading.
 */
internal fun isStructurallyAllowed(
  dependingLoading: ModuleLoadingRuleValue?,
  dependencyLoading: ModuleLoadingRuleValue?,
): Boolean {
  // Rule 1: EMBEDDED cannot depend on non-EMBEDDED sibling
  if (dependingLoading == ModuleLoadingRuleValue.EMBEDDED) {
    return dependencyLoading == ModuleLoadingRuleValue.EMBEDDED
  }
  // Rule 2: REQUIRED cannot depend on OPTIONAL/ON_DEMAND sibling
  // null (unspecified) defaults to OPTIONAL, so also fail
  if (dependingLoading == ModuleLoadingRuleValue.REQUIRED) {
    return dependencyLoading != null &&
           dependencyLoading != ModuleLoadingRuleValue.OPTIONAL &&
           dependencyLoading != ModuleLoadingRuleValue.ON_DEMAND
  }
  return true
}

// endregion

// region Layer 2: Availability Predicates

/**
 * Production plugin scope: module sets in product + ALL bundled plugins + product content.
 *
 * Note: Test plugin sources are allowed in resolution. The prod/test boundary is enforced
 * separately via validation warnings, not resolution failures. This separation allows
 * auto-add of transitive dependencies to work correctly while still providing visibility
 * into prod→test dependencies.
 */
/**
 * Production and test plugin availability are identical; keep a single implementation.
 */
private fun ResolutionQuery.isAvailableInBundledProduct(source: ContentSource, product: String): Boolean {
  return when (source.kind) {
    ContentSourceKind.MODULE_SET -> isModuleSetInProduct(source.moduleSet(), product)
    ContentSourceKind.PLUGIN -> isPluginBundledInProduct(source.plugin(), product)
    ContentSourceKind.PRODUCT -> productName(source.product()) == product
  }
}

internal val forProductionPlugin: AvailabilityPredicate = { source, product ->
  isAvailableInBundledProduct(source, product)
}

/**
 * Test plugin scope: module sets in product + ALL bundled plugins (including test) + product content.
 */
internal val forTestPlugin: AvailabilityPredicate = { source, product ->
  isAvailableInBundledProduct(source, product)
}

/**
 * DSL test plugin scope: module sets in product + bundled production plugins + product content.
 * Other test plugins are excluded, but the plugin itself is always allowed.
 *
 * @param additionalBundledPluginTargetNames Extra plugin module target names treated as bundled for this DSL test plugin.
 */
internal fun forDslTestPlugin(
  pluginName: TargetName,
  additionalBundledPluginTargetNames: Set<TargetName> = emptySet(),
): AvailabilityPredicate = { source, product ->
  when (source.kind) {
    ContentSourceKind.MODULE_SET -> isModuleSetInProduct(source.moduleSet(), product)
    ContentSourceKind.PLUGIN -> {
      val plugin = source.plugin()
      val pluginTarget = pluginName(plugin)
      pluginTarget == pluginName ||
      (!isTestPlugin(plugin) && (isPluginBundledInProduct(plugin, product) || pluginTarget in additionalBundledPluginTargetNames))
    }
    ContentSourceKind.PRODUCT -> productName(source.product()) == product
  }
}

/**
 * Global existence check: module exists somewhere (for OPTIONAL/ON_DEMAND deps).
 */
internal val existsAnywhere: AvailabilityPredicate = { _, _ -> true }

/**
 * Non-bundled production plugin scope: module must exist in a non-test source.
 * Accepts module sets (regardless of product), non-test plugins, or product content.
 * Rejects modules ONLY available in test plugins.
 */
internal val existsInNonTestSource: AvailabilityPredicate = { source, _ ->
  when (source.kind) {
    ContentSourceKind.MODULE_SET -> true  // Module sets are always OK
    ContentSourceKind.PLUGIN -> !isTestPlugin(source.plugin())  // Only non-test plugins
    ContentSourceKind.PRODUCT -> true  // Product content is always OK
  }
}

// endregion

// region Dependency Collection

/**
 * Collect plugin dependencies separated by loading mode.
 *
 * @return Pair of (requiredDeps, onDemandDeps)
 */
internal fun collectDependenciesByLoadingMode(
  contentModules: Set<ContentModuleName>,
  loadingModes: Map<ContentModuleName, ModuleLoadingRuleValue?>,
  contentModuleDeps: Map<ContentModuleName, Set<ContentModuleName>>,
  pluginModuleDependencies: Set<ContentModuleName>,
): Pair<Set<ContentModuleName>, Set<ContentModuleName>> {
  val requiredDeps = HashSet<ContentModuleName>()
  val onDemandDeps = HashSet<ContentModuleName>()

  for (moduleName in contentModules) {
    val loading = loadingModes.get(moduleName)
    val deps = contentModuleDeps.get(moduleName) ?: emptySet()

    if (loading == null ||
        loading == ModuleLoadingRuleValue.ON_DEMAND ||
        loading == ModuleLoadingRuleValue.OPTIONAL) {
      onDemandDeps.addAll(deps)
    }
    else {
      requiredDeps.addAll(deps)
    }
  }

  requiredDeps.addAll(pluginModuleDependencies)
  return requiredDeps to onDemandDeps
}

// endregion
