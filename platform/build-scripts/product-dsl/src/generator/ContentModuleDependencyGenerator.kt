// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// See docs/dependency_generation.md for dependency generation documentation
@file:Suppress("ReplaceGetOrSet", "GrazieInspection")

package org.jetbrains.intellij.build.productLayout.generator

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.DependencyClassification
import com.intellij.platform.pluginGraph.EDGE_CONTENT_MODULE_DEPENDS_ON
import com.intellij.platform.pluginGraph.EDGE_CONTENT_MODULE_DEPENDS_ON_TEST
import com.intellij.platform.pluginGraph.NODE_CONTENT_MODULE
import com.intellij.platform.pluginGraph.PluginGraph
import com.intellij.platform.pluginGraph.PluginId
import com.intellij.platform.pluginGraph.TargetDependencyScope
import com.intellij.platform.pluginGraph.isSlashNotation
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.productLayout.config.SuppressionConfig
import org.jetbrains.intellij.build.productLayout.debug
import org.jetbrains.intellij.build.productLayout.dependency.ModuleDescriptorCache
import org.jetbrains.intellij.build.productLayout.deps.ContentModuleDependencyPlan
import org.jetbrains.intellij.build.productLayout.deps.ContentModuleDependencyPlanOutput
import org.jetbrains.intellij.build.productLayout.model.error.ErrorCategory
import org.jetbrains.intellij.build.productLayout.model.error.UnsuppressedPipelineError
import org.jetbrains.intellij.build.productLayout.pipeline.ComputeContext
import org.jetbrains.intellij.build.productLayout.pipeline.DataSlot
import org.jetbrains.intellij.build.productLayout.pipeline.NodeIds
import org.jetbrains.intellij.build.productLayout.pipeline.PipelineNode
import org.jetbrains.intellij.build.productLayout.pipeline.Slots
import org.jetbrains.intellij.build.productLayout.stats.SuppressionType
import org.jetbrains.intellij.build.productLayout.stats.SuppressionUsage
import org.jetbrains.intellij.build.productLayout.util.isProductionRuntimeDependency
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsLibraryDependency
import org.jetbrains.jps.model.module.JpsModuleDependency
import org.jetbrains.jps.model.module.JpsModuleReference
import java.nio.file.Path

/**
 * Planner for content module dependency XML files.
 *
 * Processes two types of descriptor files in a single pass:
 * 1. **Main descriptors** (`moduleName.xml`) - for all content modules
 * 2. **Test descriptor files** (`moduleName._test.xml`) - for non-test-descriptor modules only
 *
 * Note: "Test descriptor modules" (content modules named `foo._test`) are a different concept.
 * They are actual content modules whose main descriptor is `foo._test.xml`. They do NOT have
 * separate test descriptor files (`foo._test._test.xml` doesn't exist).
 *
 * ## JPS Scopes vs Plugin Model
 *
 * The plugin model has no concept of TEST/COMPILE/RUNTIME scopes - it only knows about runtime dependencies.
 * However, JPS `.iml` files DO have scopes. When computing dependencies:
 *
 * - **Production deps** (`withTests=false`): Only COMPILE and RUNTIME scope JPS deps
 * - **Test deps** (`withTests=true`): All JPS deps including TEST scope
 *
 * This generator computes BOTH sets for each content module:
 * - Production deps → written to XML, stored in [EDGE_CONTENT_MODULE_DEPENDS_ON] edges
 * - Test deps → stored in [EDGE_CONTENT_MODULE_DEPENDS_ON_TEST] edges (superset of prod)
 *
 * Validation rules then choose which edge type to use:
 * - Production products use [EDGE_CONTENT_MODULE_DEPENDS_ON]
 * - Test plugins use [EDGE_CONTENT_MODULE_DEPENDS_ON_TEST]
 *
 * **Input:** All content modules with production or test content sources
 * **Output:** Dependency plans for descriptor files with `<dependencies>` sections
 *
 * **Publishes:** [Slots.CONTENT_MODULE_PLAN] for downstream writing and validation (includes both regular and test descriptor modules)
 *
 * **No dependencies** - can run immediately (level 0).
 */
internal object ContentModuleDependencyPlanner : PipelineNode {
  override val id get() = NodeIds.CONTENT_MODULE_DEPS
  override val produces: Set<DataSlot<*>> get() = setOf(Slots.CONTENT_MODULE_PLAN)

  override suspend fun execute(ctx: ComputeContext) {
    coroutineScope {
      val model = ctx.model

      // Process all content modules in parallel
      // Each module computes BOTH production and test dependencies
      data class GenerationOutput(
        @JvmField val plan: ContentModuleDependencyPlan?,
        @JvmField val suppressibleError: UnsuppressedPipelineError?,
      )

      // Single pass over all modules - no deduplication needed
      // hasContentSource filters to modules declared in plugins/products/module-sets or test plugins
      // Each content module has ONE descriptor: regular modules have moduleName.xml,
      // test descriptor modules (foo._test) have foo._test.xml - these are separate content modules
      val mainDescriptorJobs = ArrayList<Deferred<GenerationOutput>>()
      val testDescriptorJobs = ArrayList<Deferred<GenerationOutput>>()

      model.pluginGraph.query {
        contentModules { contentModule ->
          // Only process modules that have a content source (declared in some plugin/product/module-set or test plugin)
          if (!hasContentSource(contentModule.id)) {
            return@contentModules
          }

          val moduleName = contentModule.contentName()
          val isTestDescriptorModule = contentModule.isTestDescriptor

          // Each content module has ONE descriptor - process uniformly
          val job = async {
            val (plan, suppressibleError) = planContentModuleDependenciesWithBothSets(
              contentModuleName = moduleName,
              descriptorCache = model.descriptorCache,
              outputProvider = model.outputProvider,
              projectLibraryToModuleMap = model.config.projectLibraryToModuleMap,
              pluginGraph = model.pluginGraph,
              isTestDescriptor = isTestDescriptorModule,
              suppressionConfig = model.suppressionConfig,
              updateSuppressions = model.updateSuppressions,
            )
            GenerationOutput(plan, suppressibleError)
          }

          // Categorize based on module type for downstream slots
          if (isTestDescriptorModule) {
            testDescriptorJobs.add(job)
          }
          else {
            mainDescriptorJobs.add(job)
          }
        }
      }

      val mainOutputs = mainDescriptorJobs.awaitAll()
      val mainPlans = mainOutputs.mapNotNull { it.plan }
      val mainErrors = mainOutputs.mapNotNull { it.suppressibleError }

      val testOutputs = testDescriptorJobs.awaitAll()
      val testDescriptorPlans = testOutputs.mapNotNull { it.plan }
      val testErrors = testOutputs.mapNotNull { it.suppressibleError }

      val errors = mainErrors + testErrors

      // Graph is single source of truth - populate module deps for validation
      // Returns new graph instance (immutable pattern for coroutine safety)
      updateGraphWithModuleDependencyPlans(model.pluginGraph, mainPlans + testDescriptorPlans)

      ctx.emitErrors(errors)
      // Publish combined results - both regular and test descriptor modules in single output
      ctx.publish(Slots.CONTENT_MODULE_PLAN, ContentModuleDependencyPlanOutput(plans = mainPlans + testDescriptorPlans))
    }
  }
}

/**
 * Result from content module dependency generation, including any suppressible errors.
 */
internal data class ContentModuleGenerationOutput(
  @JvmField val plan: ContentModuleDependencyPlan?,
  /** Suppressible error (e.g., non-standard XML root). Will be filtered by pipeline based on suppressionKey. */
  @JvmField val suppressibleError: UnsuppressedPipelineError?,
)

/**
 * Generates dependencies for a content module, computing BOTH production and test deps.
 *
 * ## Why Both Sets?
 *
 * Content modules are production code with intrinsic dependencies determined by their JPS `.iml` files.
 * The `isTestPlugin` flag categorizes plugins for validation rules, but does NOT change what deps
 * a content module has. A content module's deps are the same whether it's in a production or test plugin.
 *
 * However, JPS `.iml` files have TEST scope deps (e.g., `intellij.libraries.hamcrest`) that are
 * only needed when running tests. These should NOT leak into production validation.
 *
 * ## Dual Edge Types
 *
 * - **Production deps** (`withTests=false`): Written to XML, stored as [EDGE_CONTENT_MODULE_DEPENDS_ON]
 * - **Test deps** (`withTests=true`): Stored as [EDGE_CONTENT_MODULE_DEPENDS_ON_TEST] (superset of prod)
 *
 * ## Validation Usage
 *
 * - Production products validate against [EDGE_CONTENT_MODULE_DEPENDS_ON] - won't see hamcrest
 * - Test plugins validate against [EDGE_CONTENT_MODULE_DEPENDS_ON_TEST] - will see hamcrest
 *
 * @see EDGE_CONTENT_MODULE_DEPENDS_ON
 * @see EDGE_CONTENT_MODULE_DEPENDS_ON_TEST
 */
internal suspend fun planContentModuleDependenciesWithBothSets(
  contentModuleName: ContentModuleName,
  descriptorCache: ModuleDescriptorCache,
  outputProvider: ModuleOutputProvider? = null,
  projectLibraryToModuleMap: Map<String, String> = emptyMap(),
  pluginGraph: PluginGraph,
  isTestDescriptor: Boolean,
  suppressionConfig: SuppressionConfig,
  updateSuppressions: Boolean,
): ContentModuleGenerationOutput {
  // Handle slash-notation modules (e.g., "intellij.restClient/intelliLang")
  // These are virtual content modules without separate JPS modules.
  // Their deps come from the descriptor XML, not from JPS - skip dependency generation.
  if (contentModuleName.isSlashNotation()) {
    return ContentModuleGenerationOutput(plan = null, suppressibleError = null)
  }

  // Compute production dependencies (written to XML)
  val prodInfo = descriptorCache.getOrAnalyze(contentModuleName.value)
                 ?: return ContentModuleGenerationOutput(plan = null, suppressibleError = null)

  if (prodInfo.skipDependencyGeneration) {
    return ContentModuleGenerationOutput(plan = null, suppressibleError = prodInfo.suppressibleError)
  }

  val plan = buildContentModuleDependencyPlanFromInfoWithBothSets(
    contentModuleName = contentModuleName,
    prodInfo = prodInfo,
    outputProvider = outputProvider,
    projectLibraryToModuleMap = projectLibraryToModuleMap,
    graph = pluginGraph,
    suppressionConfig = suppressionConfig,
    updateSuppressions = updateSuppressions,
    isTestDescriptor = isTestDescriptor,
  )
  return ContentModuleGenerationOutput(plan = plan, suppressibleError = prodInfo.suppressibleError)
}

/**
 * Core implementation that computes BOTH production and test dependencies.
 * 
 * - Production deps: Written to XML, used for [EDGE_CONTENT_MODULE_DEPENDS_ON]
 * - Test deps: Stored in result.testDependencies, used for [EDGE_CONTENT_MODULE_DEPENDS_ON_TEST]
 *
 * Note: Test deps are computed from JPS module directly (not from a separate descriptor info),
 * since the same module's JPS deps are queried with different `withTests` flags.
 *
 * ## Test Module Handling
 *
 * Content modules ending with `._test` are test modules (test descriptors declared in module sets).
 * These modules need their TEST scope JPS dependencies included in the XML because they run in
 * a test context. For these modules, we use `withTests=true` when computing "production" deps.
 */
private fun buildContentModuleDependencyPlanFromInfoWithBothSets(
  contentModuleName: ContentModuleName,
  prodInfo: ModuleDescriptorCache.DescriptorInfo,
  outputProvider: ModuleOutputProvider?,
  projectLibraryToModuleMap: Map<String, String>,
  graph: PluginGraph,
  suppressionConfig: SuppressionConfig,
  updateSuppressions: Boolean,
  isTestDescriptor: Boolean,
): ContentModuleDependencyPlan {
  // Skip XML modification for modules with non-standard XML root
  if (prodInfo.suppressibleError?.category == ErrorCategory.NON_STANDARD_DESCRIPTOR_ROOT) {
    return ContentModuleDependencyPlan(
      contentModuleName = contentModuleName,
      descriptorPath = prodInfo.descriptorPath,
      descriptorContent = prodInfo.content,
      moduleDependencies = emptyList(),
      pluginDependencies = emptyList(),
      testDependencies = emptyList(),
      existingXmlModuleDependencies = emptySet(),
      existingXmlPluginDependencies = emptySet(),
      preserveExistingPluginDependencies = emptySet(),
      writtenPluginDependencies = emptyList(),
      requiredPluginDependencies = emptySet(),
      suppressedModules = emptySet(),
      suppressedPlugins = emptySet(),
      suppressionUsages = emptyList(),
      suppressibleError = prodInfo.suppressibleError,
    )
  }

  // Suppression semantics: add ALL deps by default, suppress specific entries
  // Both filters return true = include, false = suppress
  val suppressedModules = suppressionConfig.getSuppressedModules(contentModuleName)
  val suppressedPlugins = suppressionConfig.getSuppressedPlugins(contentModuleName)

  val existingXmlModules = prodInfo.existingModuleDependencies.toSet()
  val existingXmlPlugins = prodInfo.existingPluginDependencies.toSet()
  val existingXmlModulesAsContentModuleName = existingXmlModules.mapTo(HashSet(), ::ContentModuleName)
  val existingXmlPluginsAsPluginId = existingXmlPlugins.mapTo(HashSet(), ::PluginId)

  val prodModuleDeps: List<String>
  val testModuleDeps = ArrayList<String>()
  val pluginDeps = ArrayList<String>()
  val suppressionUsages = ArrayList<SuppressionUsage>()

  // Compute dependencies written to XML using graph EDGE_TARGET_DEPENDS_ON.
  // Whether TEST scope deps are included is decided by shouldIncludeTestScopeForWrittenDeps. Production descriptors may be
  // owned by plugins marked as test plugins, but their generated XML must still follow production JPS runtime scope.
  val includeTestScopeForWrittenDeps = shouldIncludeTestScopeForWrittenDeps(
    graph = graph,
    outputProvider = outputProvider,
    contentModuleName = contentModuleName,
    descriptorPath = prodInfo.descriptorPath,
    isTestDescriptor = isTestDescriptor,
  )
  val prodGraphDeps = computeJpsDeps(
    graph = graph,
    moduleName = contentModuleName,
    includeTestScope = includeTestScopeForWrittenDeps,
    outputProvider = outputProvider,
    projectLibraryToModuleMap = projectLibraryToModuleMap,
  )
  val prodGraphModuleDeps = prodGraphDeps.moduleDeps
  val prodGraphPluginDeps = prodGraphDeps.pluginDeps
  val testGraphModuleDeps = computeJpsDeps(
    graph = graph,
    moduleName = contentModuleName,
    includeTestScope = true,
    outputProvider = outputProvider,
    projectLibraryToModuleMap = projectLibraryToModuleMap,
  ).moduleDeps
  val nonProductionGraphModuleDeps = testGraphModuleDeps - prodGraphModuleDeps
  val xmlOnlySuppressionCandidateModuleDeps = existingXmlModulesAsContentModuleName.filterTo(LinkedHashSet()) {
    it !in nonProductionGraphModuleDeps
  }

  val moduleHandling = computeExistingDependencyHandling(
    updateSuppressions = updateSuppressions,
    existingXmlDeps = existingXmlModulesAsContentModuleName,
    jpsDeps = prodGraphModuleDeps,
    suppressedDeps = suppressedModules,
    xmlOnlySuppressionCandidateDeps = xmlOnlySuppressionCandidateModuleDeps,
  )

  // A `<plugin id>` entry hard-gates module loading: if the plugin is absent from the layout, the content module and
  // everything depending on it is silently excluded (that is how IJPL-248736 broke the Rider TestNG entry point).
  // A descriptor generated with test scope pulls in TEST/PROVIDED scope JPS deps, which routinely point at plugins that
  // are not part of the layout the tests run in, so such a descriptor must never *introduce* a plugin gate.
  // Plugin deps already declared in the XML are kept - same grandfathering as isTestOnlyContentModule in
  // TestPluginDependencyPlanner (IJPL-241684).
  val requiredPluginDeps = if (includeTestScopeForWrittenDeps) {
    prodGraphPluginDeps.filterTo(LinkedHashSet()) { it in existingXmlPluginsAsPluginId }
  }
  else {
    prodGraphPluginDeps
  }
  if (requiredPluginDeps.size != prodGraphPluginDeps.size) {
    debug("filterDeps") {
      "omit new plugin deps for test-scope descriptor ${contentModuleName.value}: " +
      (prodGraphPluginDeps - requiredPluginDeps).map { it.value }.sorted().joinToString()
    }
  }

  val pluginHandling = computeExistingDependencyHandling(
    updateSuppressions = updateSuppressions,
    existingXmlDeps = existingXmlPluginsAsPluginId,
    jpsDeps = requiredPluginDeps,
    suppressedDeps = suppressedPlugins,
    semanticallyPreservedExistingDeps = computeAliasPreservedPluginDeps(graph, existingXmlPluginsAsPluginId),
  )
  val effectiveSuppressedModules = moduleHandling.effectiveSuppressedDeps
  val effectiveSuppressedPlugins = pluginHandling.effectiveSuppressedDeps

  prodModuleDeps = collectModuleDepsWithSuppressions(
    contentModuleName = contentModuleName,
    dependencies = prodGraphModuleDeps,
    suppressedModules = effectiveSuppressedModules,
    suppressionUsages = suppressionUsages,
  )

  for (pluginId in requiredPluginDeps) {
    if (effectiveSuppressedPlugins.contains(pluginId)) {
      suppressionUsages.add(SuppressionUsage(contentModuleName, pluginId.value, SuppressionType.PLUGIN_DEP))
    }
    else {
      pluginDeps.add(pluginId.value)
    }
  }

  // TEST dependencies include TEST scope and use the same JPS/runtime filtering path as production deps.
  for (depModule in testGraphModuleDeps) {
    val depName = depModule.value
    if (!effectiveSuppressedModules.contains(depModule)) {
      testModuleDeps.add(depName)
    }
  }

  // Track suppressions that prevent removal: existing XML deps not in JPS graph
  for (existingDep in existingXmlModulesAsContentModuleName) {
    val notInGraph = existingDep !in prodGraphModuleDeps
    if (notInGraph && effectiveSuppressedModules.contains(existingDep)) {
      // Suppression keeps this XML dep - report it
      suppressionUsages.add(SuppressionUsage(contentModuleName, existingDep.value, SuppressionType.MODULE_DEP))
    }
  }

  // Track plugin suppressions that prevent removal: existing XML plugin deps not in JPS
  for (existingPlugin in existingXmlPluginsAsPluginId) {
    val notInJps = existingPlugin !in requiredPluginDeps
    if (notInJps && effectiveSuppressedPlugins.contains(existingPlugin)) {
      // Suppression keeps this XML plugin dep - report it
      suppressionUsages.add(SuppressionUsage(contentModuleName, existingPlugin.value, SuppressionType.PLUGIN_DEP))
    }
  }

  val allWrittenPluginDeps = (pluginHandling.preserveExistingDeps.map { it.value } + pluginDeps).distinct().sorted()


  return ContentModuleDependencyPlan(
    contentModuleName = contentModuleName,
    descriptorPath = prodInfo.descriptorPath,
    descriptorContent = prodInfo.content,
    moduleDependencies = prodModuleDeps.distinct().sorted().map(::ContentModuleName),
    pluginDependencies = pluginDeps.distinct().sorted().map(::PluginId),
    testDependencies = testModuleDeps.distinct().sorted().map(::ContentModuleName),
    existingXmlModuleDependencies = existingXmlModulesAsContentModuleName,
    existingXmlPluginDependencies = existingXmlPluginsAsPluginId,
    preserveExistingPluginDependencies = pluginHandling.preserveExistingDeps,
    writtenPluginDependencies = allWrittenPluginDeps.map(::PluginId),
    requiredPluginDependencies = requiredPluginDeps,
    suppressedModules = effectiveSuppressedModules,
    suppressedPlugins = effectiveSuppressedPlugins,
    suppressionUsages = suppressionUsages,
  )
}

/**
 * Decides whether TEST-scope JPS deps belong in the descriptor's generated `<dependencies>`.
 *
 * True in exactly three cases:
 * 1. the descriptor is a test descriptor (`foo._test.xml`);
 * 2. the module is test support (`*.testFramework`, IDE starter, …) and has no production content source;
 * 3. the descriptor file itself lies under a JPS test source root (e.g. `testResources/foo.tests.xml`).
 *
 * Case 3 is what makes test-only modules work; it is deliberately based on descriptor location, not on the module name.
 */
private fun shouldIncludeTestScopeForWrittenDeps(
  graph: PluginGraph,
  outputProvider: ModuleOutputProvider?,
  contentModuleName: ContentModuleName,
  descriptorPath: Path,
  isTestDescriptor: Boolean,
): Boolean {
  if (isTestDescriptor) {
    return true
  }
  if (isTestSupportContentModule(contentModuleName, descriptorPath) && !hasProductionContentSource(graph, contentModuleName)) {
    return true
  }
  val module = outputProvider?.findModule(contentModuleName.value) ?: return false
  return module.sourceRoots.any { it.rootType.isForTests && descriptorPath.startsWith(it.path) }
}

private fun hasProductionContentSource(graph: PluginGraph, contentModuleName: ContentModuleName): Boolean {
  return graph.query {
    val module = contentModule(contentModuleName) ?: return@query false
    hasProductionContentSource(module.id)
  }
}

private fun isTestSupportContentModule(moduleName: ContentModuleName, descriptorPath: Path): Boolean {
  val name = moduleName.value
  return name.endsWith(".testFramework") ||
         name.contains(".testFramework.") ||
         name.endsWith("TestFramework") ||
         name.endsWith(".testGuiFramework") ||
         name.contains(".test.framework") ||
         name.startsWith("intellij.rider.test.framework") ||
         name == "intellij.tools.testsBootstrap" ||
         name == "intellij.idea.tools.launch" ||
         name.startsWith("intellij.ide.starter.") ||
         name.startsWith("intellij.tools.ide.starter.") ||
         name.startsWith("intellij.tools.ide.metrics.") ||
         descriptorPath.toString().contains("/testFramework/")
}

/**
 * Update graph with module dependencies (swaps internal store).
 *
 * Called by ContentModuleDependencyPlanner after computing effective deps.
 * Populates TWO edge types:
 * - [EDGE_CONTENT_MODULE_DEPENDS_ON]: Production deps (from writtenDependencies + suppressed existing XML deps)
 * - [EDGE_CONTENT_MODULE_DEPENDS_ON_TEST]: Test deps (from testDependencies, superset of prod)
 *
 * **Orphan handling**: Dependencies that don't exist in the graph (orphan modules) are
 * added as new nodes WITHOUT content source edges. This allows validation to detect them
 * using [com.intellij.platform.pluginGraph.GraphScope.hasContentSource]. See `docs/validation-rules.md#resolved-vs-orphan-modules`.
 *
 * **Existing XML deps**: Included in production edges only when explicitly suppressed
 * (i.e., intentionally preserved despite not being in JPS).
 *
 * Swaps the internal store in-place (thread-safe via @Volatile).
 *
 * @param plans List of dependency plans from generation
 */
internal fun updateGraphWithModuleDependencyPlans(graph: PluginGraph, plans: List<ContentModuleDependencyPlan>) {
  if (plans.isEmpty()) {
    return
  }

  val store = graph.storeForBuilder()

  // Phase 1: Collect all orphan modules (deps not in graph)
  val orphanModules = LinkedHashSet<String>()
  for (plan in plans) {
    if (store.nodeId(plan.contentModuleName.value, NODE_CONTENT_MODULE) < 0) continue

    // Production deps: writtenDependencies + suppressed existing XML deps
    for (depModule in plan.moduleDependencies) {
      if (store.nodeId(depModule.value, NODE_CONTENT_MODULE) < 0) {
        orphanModules.add(depModule.value)
      }
    }
    val preservedXmlModuleDeps = plan.existingXmlModuleDependencies.filter { it in plan.suppressedModules }
    for (depModule in preservedXmlModuleDeps) {
      if (store.nodeId(depModule.value, NODE_CONTENT_MODULE) < 0) {
        orphanModules.add(depModule.value)
      }
    }

    // Test deps
    for (depModule in plan.testDependencies) {
      if (store.nodeId(depModule.value, NODE_CONTENT_MODULE) < 0) {
        orphanModules.add(depModule.value)
      }
    }
  }

  // Phase 2: Build new store with orphan nodes added
  val newStore = store.toMutableStore(
    lazyNameIndex = true,
    descriptorFlagsComplete = false,
  )

  val moduleIndex = newStore.mutableNameIndex(NODE_CONTENT_MODULE)

  // Add orphan nodes
  for (orphanName in orphanModules) {
    val nodeId = newStore.names.size
    newStore.names.add(orphanName)
    newStore.kinds.add(NODE_CONTENT_MODULE)
    moduleIndex.put(orphanName, nodeId)
  }

  // Phase 3: Populate edges using the API
  for (plan in plans) {
    val fromId = moduleIndex.getOrDefault(plan.contentModuleName.value, -1)
    if (fromId < 0) continue

    // Populate production edges (writtenDependencies + suppressed existing XML deps)
    for (depModule in plan.moduleDependencies) {
      val toId = moduleIndex.getOrDefault(depModule.value, -1)
      if (toId >= 0) {
        newStore.addEdge(EDGE_CONTENT_MODULE_DEPENDS_ON, fromId, toId)
      }
    }
    // Also add suppressed existing XML deps (explicitly preserved)
    val preservedXmlModuleDeps = plan.existingXmlModuleDependencies.filter { it in plan.suppressedModules }
    for (depModule in preservedXmlModuleDeps) {
      val toId = moduleIndex.getOrDefault(depModule.value, -1)
      if (toId >= 0) {
        newStore.addEdge(EDGE_CONTENT_MODULE_DEPENDS_ON, fromId, toId)
      }
    }

    // Populate test edges (testDependencies - superset of prod)
    for (depModule in plan.testDependencies) {
      val toId = moduleIndex.getOrDefault(depModule.value, -1)
      if (toId >= 0) {
        newStore.addEdge(EDGE_CONTENT_MODULE_DEPENDS_ON_TEST, fromId, toId)
      }
    }
  }

  // Swap store atomically
  graph.setCurrentStore(newStore.freeze())
}

internal data class JpsDeps(
  val moduleDeps: Set<ContentModuleName>,
  val pluginDeps: Set<PluginId>,
)

/**
 * Computes JPS dependencies for a content module with scope filtering.
 *
 * Returns both module and plugin dependencies from the graph's EDGE_TARGET_DEPENDS_ON edges.
 * Uses scope filtering to exclude TEST scope dependencies when needed.
 *
 * Every production-scope JPS dependency that maps to a content module is returned: dependencies are
 * declared explicitly even when the target is embedded in every product.
 *
 * @param moduleName The content module name
 * @param includeTestScope If false, excludes TEST scope dependencies
 */
internal fun computeJpsDeps(
  graph: PluginGraph,
  moduleName: ContentModuleName,
  includeTestScope: Boolean,
  outputProvider: ModuleOutputProvider? = null,
  projectLibraryToModuleMap: Map<String, String> = emptyMap(),
): JpsDeps {
  val allowedModuleDeps = computeDirectJpsRuntimeModuleDeps(
    outputProvider = outputProvider,
    moduleName = moduleName,
    includeTestScope = includeTestScope,
    projectLibraryToModuleMap = projectLibraryToModuleMap,
  )
  val moduleDeps = HashSet<ContentModuleName>()
  val pluginDeps = HashSet<PluginId>()
  graph.query {
    val mod = contentModule(moduleName) ?: return@query
    mod.backedBy { target ->
      target.dependsOn { dep ->
        val scope = dep.scope()
        // PRODUCTION_RUNTIME includes COMPILE and RUNTIME, but NOT PROVIDED and NOT TEST
        // See JpsJavaDependencyScope.java - PROVIDED is only in PRODUCTION_COMPILE, not PRODUCTION_RUNTIME
        if (!includeTestScope && (scope == TargetDependencyScope.TEST || scope == TargetDependencyScope.PROVIDED)) {
          return@dependsOn
        }

        when (val c = classifyTarget(dep.targetId)) {
          is DependencyClassification.ModuleDep -> {
            if (c.moduleName == moduleName) {
              return@dependsOn
            }
            if (allowedModuleDeps != null && c.moduleName !in allowedModuleDeps) {
              return@dependsOn
            }
            moduleDeps.add(c.moduleName)
          }
          is DependencyClassification.PluginDep -> pluginDeps.add(c.pluginId)
          DependencyClassification.Skip -> {}
        }
      }
    }
  }

  return JpsDeps(moduleDeps, pluginDeps)
}

private fun computeDirectJpsRuntimeModuleDeps(
  outputProvider: ModuleOutputProvider?,
  moduleName: ContentModuleName,
  includeTestScope: Boolean,
  projectLibraryToModuleMap: Map<String, String>,
): Set<ContentModuleName>? {
  val module = outputProvider?.findModule(moduleName.value) ?: return null
  val javaExtensionService = JpsJavaExtensionService.getInstance()
  val libraryToModuleMap = projectLibraryToModuleMap.ifEmpty { outputProvider.getProjectLibraryToModuleMap() }
  val result = HashSet<ContentModuleName>()

  for (element in module.dependenciesList.dependencies) {
    if (!isProductionRuntimeDependency(element, javaExtensionService, withTests = includeTestScope)) {
      continue
    }
    when (element) {
      is JpsModuleDependency -> result.add(ContentModuleName(element.moduleReference.moduleName))
      is JpsLibraryDependency -> {
        val libraryReference = element.libraryReference
        if (libraryReference.parentReference is JpsModuleReference) {
          continue
        }
        val libraryModuleName = libraryToModuleMap.get(libraryReference.libraryName) ?: continue
        result.add(ContentModuleName(libraryModuleName))
      }
    }
  }

  result.remove(moduleName)
  return result
}
