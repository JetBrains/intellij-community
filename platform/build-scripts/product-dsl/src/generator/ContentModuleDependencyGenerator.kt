// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// See docs/dependency_generation.md for dependency generation documentation
@file:Suppress("ReplaceGetOrSet", "GrazieInspection")

package org.jetbrains.intellij.build.productLayout.generator

import androidx.collection.MutableIntList
import androidx.collection.MutableIntObjectMap
import androidx.collection.MutableObjectList
import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.DependencyClassification
import com.intellij.platform.pluginGraph.EDGE_CONTENT_MODULE_DEPENDS_ON
import com.intellij.platform.pluginGraph.EDGE_CONTENT_MODULE_DEPENDS_ON_TEST
import com.intellij.platform.pluginGraph.MutablePluginGraphStore
import com.intellij.platform.pluginGraph.NODE_CONTENT_MODULE
import com.intellij.platform.pluginGraph.PluginGraph
import com.intellij.platform.pluginGraph.PluginId
import com.intellij.platform.pluginGraph.TargetDependencyScope
import com.intellij.platform.pluginGraph.isSlashNotation
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jetbrains.intellij.build.productLayout.LIB_MODULE_PREFIX
import org.jetbrains.intellij.build.productLayout.config.SuppressionConfig
import org.jetbrains.intellij.build.productLayout.debug
import org.jetbrains.intellij.build.productLayout.dependency.ModuleDescriptorCache
import org.jetbrains.intellij.build.productLayout.model.error.ErrorCategory
import org.jetbrains.intellij.build.productLayout.model.error.UnsuppressedPipelineError
import org.jetbrains.intellij.build.productLayout.pipeline.ComputeContext
import org.jetbrains.intellij.build.productLayout.pipeline.ContentModuleOutput
import org.jetbrains.intellij.build.productLayout.pipeline.DataSlot
import org.jetbrains.intellij.build.productLayout.pipeline.NodeIds
import org.jetbrains.intellij.build.productLayout.pipeline.PipelineNode
import org.jetbrains.intellij.build.productLayout.pipeline.Slots
import org.jetbrains.intellij.build.productLayout.stats.DependencyFileResult
import org.jetbrains.intellij.build.productLayout.stats.FileChangeStatus
import org.jetbrains.intellij.build.productLayout.stats.SuppressionType
import org.jetbrains.intellij.build.productLayout.stats.SuppressionUsage
import org.jetbrains.intellij.build.productLayout.util.FileUpdateStrategy
import org.jetbrains.intellij.build.productLayout.xml.updateXmlDependencies

/**
 * Generator for content module dependency XML files.
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
 * **Output:** Updated descriptor files with `<dependencies>` sections
 *
 * **Publishes:** [Slots.CONTENT_MODULE] for downstream validation (includes both regular and test descriptor modules)
 *
 * **No dependencies** - can run immediately (level 0).
 */
internal object ContentModuleDependencyGenerator : PipelineNode {
  override val id get() = NodeIds.CONTENT_MODULE_DEPS
  override val produces: Set<DataSlot<*>> get() = setOf(Slots.CONTENT_MODULE)

  override suspend fun execute(ctx: ComputeContext) {
    coroutineScope {
      val model = ctx.model

      // Process all content modules in parallel
      // Each module computes BOTH production and test dependencies
      data class GenerationOutput(
        @JvmField val result: DependencyFileResult?,
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
            val (result, suppressibleError) = generateContentModuleDependenciesWithBothSets(
              contentModuleName = moduleName,
              descriptorCache = model.descriptorCache,
              pluginGraph = model.pluginGraph,
              isTestDescriptor = isTestDescriptorModule,
              suppressionConfig = model.suppressionConfig,
              strategy = model.fileUpdater,
              updateSuppressions = model.updateSuppressions,
              libraryModuleFilter = model.config.libraryModuleFilter,
            )
            GenerationOutput(result, suppressibleError)
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
      val mainResults = mainOutputs.mapNotNull { it.result }
      val mainErrors = mainOutputs.mapNotNull { it.suppressibleError }

      val testOutputs = testDescriptorJobs.awaitAll()
      val testDescriptorResults = testOutputs.mapNotNull { it.result }
      val testErrors = testOutputs.mapNotNull { it.suppressibleError }

      val errors = mainErrors + testErrors

      // Graph is single source of truth - populate module deps for validation
      // Returns new graph instance (immutable pattern for coroutine safety)
      updateWithModuleDependencies(model.pluginGraph, mainResults + testDescriptorResults)

      ctx.emitErrors(errors)
      // Publish combined results - both regular and test descriptor modules in single output
      ctx.publish(Slots.CONTENT_MODULE, ContentModuleOutput(files = mainResults + testDescriptorResults))
    }
  }
}

/**
 * Result from content module dependency generation, including any suppressible errors.
 */
internal data class ContentModuleGenerationOutput(
  @JvmField val result: DependencyFileResult?,
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
internal suspend fun generateContentModuleDependenciesWithBothSets(
  contentModuleName: ContentModuleName,
  descriptorCache: ModuleDescriptorCache,
  pluginGraph: PluginGraph,
  isTestDescriptor: Boolean,
  suppressionConfig: SuppressionConfig,
  strategy: FileUpdateStrategy,
  updateSuppressions: Boolean = false,
  libraryModuleFilter: (String) -> Boolean,
): ContentModuleGenerationOutput {
  // Handle slash-notation modules (e.g., "intellij.restClient/intelliLang")
  // These are virtual content modules without separate JPS modules.
  // Their deps come from the descriptor XML, not from JPS - skip dependency generation.
  if (contentModuleName.isSlashNotation()) {
    return ContentModuleGenerationOutput(result = null, suppressibleError = null)
  }

  // Compute production dependencies (written to XML)
  val prodInfo = descriptorCache.getOrAnalyze(contentModuleName.value)
                 ?: return ContentModuleGenerationOutput(result = null, suppressibleError = null)

  if (prodInfo.skipDependencyGeneration) {
    return ContentModuleGenerationOutput(result = null, suppressibleError = prodInfo.suppressibleError)
  }

  val result = generateContentModuleDependenciesFromInfoWithBothSets(
    contentModuleName = contentModuleName,
    prodInfo = prodInfo,
    graph = pluginGraph,
    suppressionConfig = suppressionConfig,
    strategy = strategy,
    isTestDescriptor = isTestDescriptor,
    updateSuppressions = updateSuppressions,
    libraryModuleFilter = libraryModuleFilter,
  )
  return ContentModuleGenerationOutput(result = result, suppressibleError = prodInfo.suppressibleError)
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
private fun generateContentModuleDependenciesFromInfoWithBothSets(
  contentModuleName: ContentModuleName,
  prodInfo: ModuleDescriptorCache.DescriptorInfo,
  graph: PluginGraph,
  suppressionConfig: SuppressionConfig,
  strategy: FileUpdateStrategy,
  isTestDescriptor: Boolean,
  updateSuppressions: Boolean,
  libraryModuleFilter: (String) -> Boolean,
): DependencyFileResult {
  // Skip XML modification for modules with non-standard XML root
  if (prodInfo.suppressibleError?.category == ErrorCategory.NON_STANDARD_DESCRIPTOR_ROOT) {
    return DependencyFileResult(
      contentModuleName = contentModuleName,
      descriptorPath = prodInfo.descriptorPath,
      status = FileChangeStatus.UNCHANGED,
      writtenDependencies = emptyList(),
      testDependencies = emptyList(),
      existingXmlModuleDependencies = emptySet(),
      writtenPluginDependencies = emptyList(),
      allJpsPluginDependencies = emptySet(),
      suppressionUsages = emptyList(),
    )
  }

  // Suppression semantics: add ALL deps by default, suppress specific entries
  // Both filters return true = include, false = suppress
  val suppressedModules = suppressionConfig.getSuppressedModules(contentModuleName)
  val suppressedPlugins = suppressionConfig.getSuppressedPlugins(contentModuleName)

  val prodModuleDeps: List<String>
  val testModuleDeps = ArrayList<String>()
  val pluginDeps = ArrayList<String>()
  val allJpsPluginDeps = ArrayList<PluginId>()
  val suppressionUsages = ArrayList<SuppressionUsage>()

  val existingXmlModules = prodInfo.existingModuleDependencies.toSet()
  val existingXmlModulesAsContentModuleName = existingXmlModules.mapTo(HashSet(), ::ContentModuleName)

  // Compute PRODUCTION dependencies using graph EDGE_TARGET_DEPENDS_ON
  // For test modules (._test), include TEST scope deps since they run in test context
  val prodGraphDeps = graph.query {
    computeJpsDeps(
      graph = graph,
      moduleName = contentModuleName,
      includeTestScope = isTestDescriptor,
      libraryModuleFilter = libraryModuleFilter,
    )
  }
  val prodGraphModuleDeps = prodGraphDeps.moduleDeps
  val prodGraphPluginDeps = prodGraphDeps.pluginDeps
  val prodFilteredEmbeddedDeps = prodGraphDeps.filteredEmbeddedModuleDeps.filterTo(LinkedHashSet()) { dep -> dep in prodGraphModuleDeps }

  prodModuleDeps = collectModuleDepsWithSuppressions(
    contentModuleName = contentModuleName,
    dependencies = prodGraphModuleDeps,
    existingXmlModules = existingXmlModulesAsContentModuleName,
    suppressedModules = suppressedModules,
    updateSuppressions = updateSuppressions,
    suppressionUsages = suppressionUsages,
  )

  val existingXmlPlugins = prodInfo.existingPluginDependencies.mapTo(HashSet(), ::PluginId)
  for (pluginId in prodGraphPluginDeps) {
    allJpsPluginDeps.add(pluginId)
    val isNewPlugin = pluginId !in existingXmlPlugins

    if (updateSuppressions && isNewPlugin) {
      // Freeze mode: suppress new plugin deps to prevent adding them
      suppressionUsages.add(SuppressionUsage(contentModuleName, pluginId.value, SuppressionType.PLUGIN_DEP))
    }
    else if (suppressedPlugins.contains(pluginId)) {
      // Normal mode: use existing suppressions
      suppressionUsages.add(SuppressionUsage(contentModuleName, pluginId.value, SuppressionType.PLUGIN_DEP))
    }
    else {
      pluginDeps.add(pluginId.value)
    }
  }

  // Compute TEST dependencies using graph (includes TEST scope)
  // Apply same filter as production deps for consistency
  val testGraphModuleDeps = computeJpsDeps(
    graph = graph,
    moduleName = contentModuleName,
    includeTestScope = true,
    libraryModuleFilter = libraryModuleFilter,
  ).moduleDeps

  for (depModule in testGraphModuleDeps) {
    val depName = depModule.value
    if (!suppressedModules.contains(depModule)) {
      testModuleDeps.add(depName)
    }
  }

  // Track suppressions that prevent removal: existing XML deps not in JPS graph
  val prodDepNames = prodGraphModuleDeps.mapTo(HashSet()) { it.value }
  val prodFilteredEmbeddedDepNames = prodFilteredEmbeddedDeps.mapTo(HashSet()) { it.value }
  for (existingDep in existingXmlModules) {
    val notInGraph = existingDep !in prodDepNames
    if (notInGraph) {
      if (updateSuppressions) {
        if (existingDep in prodFilteredEmbeddedDepNames) {
          debug("filterDeps") {
            "preserve embedded dep via suppression for ${contentModuleName.value} -> $existingDep"
          }
        }
        // Freeze mode: suppress removal of existing deps not in JPS
        suppressionUsages.add(SuppressionUsage(contentModuleName, existingDep, SuppressionType.MODULE_DEP))
      }
      else if (suppressedModules.contains(ContentModuleName(existingDep))) {
        // Normal mode: suppression keeps this XML dep - report it
        suppressionUsages.add(SuppressionUsage(contentModuleName, existingDep, SuppressionType.MODULE_DEP))
      }
    }
  }

  // Track plugin suppressions that prevent removal: existing XML plugin deps not in JPS
  val jpsPluginNames = prodGraphPluginDeps.mapTo(HashSet()) { it.value }
  for (existingPlugin in prodInfo.existingPluginDependencies) {
    val notInJps = existingPlugin !in jpsPluginNames
    if (notInJps) {
      if (updateSuppressions) {
        // Freeze mode: suppress removal of existing plugin deps not in JPS
        suppressionUsages.add(SuppressionUsage(contentModuleName, existingPlugin, SuppressionType.PLUGIN_DEP))
      }
      else if (suppressedPlugins.contains(PluginId(existingPlugin))) {
        // Normal mode: suppression keeps this XML plugin dep - report it
        suppressionUsages.add(SuppressionUsage(contentModuleName, existingPlugin, SuppressionType.PLUGIN_DEP))
      }
    }
  }

  val status = if (updateSuppressions) {
    FileChangeStatus.UNCHANGED
  }
  else {
    updateXmlDependencies(
      path = prodInfo.descriptorPath,
      content = prodInfo.content,
      moduleDependencies = prodModuleDeps.distinct().sorted(),
      pluginDependencies = pluginDeps.distinct().sorted(),
      preserveExistingModule = { moduleName ->
        // Normal mode: only preserve if suppressed
        suppressedModules.contains(ContentModuleName(moduleName))
      },
      preserveExistingPlugin = { pluginName ->
        // Normal mode: only preserve if suppressed
        suppressedPlugins.contains(PluginId(pluginName))
      },
      strategy = strategy,
    )
  }

  val allWrittenPluginDeps = (prodInfo.existingPluginDependencies + pluginDeps).distinct().sorted()

  return DependencyFileResult(
    contentModuleName = contentModuleName,
    descriptorPath = prodInfo.descriptorPath,
    status = status,
    writtenDependencies = prodModuleDeps.sorted().map(::ContentModuleName),
    testDependencies = testModuleDeps.distinct().sorted().map(::ContentModuleName),
    existingXmlModuleDependencies = prodInfo.existingModuleDependencies.mapTo(HashSet(), ::ContentModuleName),
    writtenPluginDependencies = allWrittenPluginDeps.map(::PluginId),
    allJpsPluginDependencies = allJpsPluginDeps.distinct().toSet(),
    suppressionUsages = suppressionUsages,
  )
}

/**
 * Update graph with module dependencies (swaps internal store).
 *
 * Called by ContentModuleDependencyGenerator after computing effective deps.
 * Populates TWO edge types:
 * - [EDGE_CONTENT_MODULE_DEPENDS_ON]: Production deps (from writtenDependencies + existingXmlModuleDependencies)
 * - [EDGE_CONTENT_MODULE_DEPENDS_ON_TEST]: Test deps (from testDependencies, superset of prod)
 *
 * **Orphan handling**: Dependencies that don't exist in the graph (orphan modules) are
 * added as new nodes WITHOUT content source edges. This allows validation to detect them
 * using [com.intellij.platform.pluginGraph.GraphScope.hasContentSource]. See `docs/validation-rules.md#resolved-vs-orphan-modules`.
 *
 * **Existing XML deps**: Always included in production edges because they were explicitly
 * written by someone. If they don't resolve, they need to be detected as errors.
 *
 * Swaps the internal store in-place (thread-safe via @Volatile).
 *
 * @param results List of dependency file results from generation
 */
internal fun updateWithModuleDependencies(graph: PluginGraph, results: List<DependencyFileResult>) {
  if (results.isEmpty()) {
    return
  }

  val store = graph.storeForBuilder()

  // Phase 1: Collect all orphan modules (deps not in graph)
  val orphanModules = LinkedHashSet<String>()
  for (result in results) {
    if (store.nodeId(result.contentModuleName.value, NODE_CONTENT_MODULE) < 0) continue

    // Production deps: writtenDependencies + existingXmlModuleDependencies
    for (depModule in result.writtenDependencies) {
      if (store.nodeId(depModule.value, NODE_CONTENT_MODULE) < 0) {
        orphanModules.add(depModule.value)
      }
    }
    for (depModule in result.existingXmlModuleDependencies) {
      if (store.nodeId(depModule.value, NODE_CONTENT_MODULE) < 0) {
        orphanModules.add(depModule.value)
      }
    }

    // Test deps
    for (depModule in result.testDependencies) {
      if (store.nodeId(depModule.value, NODE_CONTENT_MODULE) < 0) {
        orphanModules.add(depModule.value)
      }
    }
  }

  // Phase 2: Build new store with orphan nodes added
  val newNames = MutableObjectList<String>(store.names.size).also { list ->
    store.names.forEach { list.add(it) }
  }
  val newKinds = MutableIntList(store.kinds.size).also { list ->
    store.kinds.forEach { list.add(it) }
  }
  val newNameIndex = store.nameIndex.copyOf()
  val nameIndexOwned = BooleanArray(newNameIndex.size)

  val pluginIdsCopy = MutableIntObjectMap<String>(store.pluginIds.size).also { map ->
    store.pluginIds.forEach { key, value -> map[key] = value }
  }
  val aliasesCopy = MutableIntObjectMap<Array<String>>(store.aliases.size).also { map ->
    store.aliases.forEach { key, value -> map[key] = value }
  }

  // Phase 3: Create new store and populate edges using the API
  val (newOutEdges, newInEdges) = store.copyEdgeMaps()
  val newStore = MutablePluginGraphStore(
    names = newNames,
    kinds = newKinds,
    pluginIds = pluginIdsCopy,
    aliases = aliasesCopy,
    outEdges = newOutEdges,
    inEdges = newInEdges,
    nameIndex = newNameIndex,
    nameIndexOwned = nameIndexOwned,
  )

  val moduleIndex = newStore.mutableNameIndex(NODE_CONTENT_MODULE)

  // Add orphan nodes
  for (orphanName in orphanModules) {
    val nodeId = newNames.size
    newNames.add(orphanName)
    newKinds.add(NODE_CONTENT_MODULE)
    moduleIndex.put(orphanName, nodeId)
  }

  for (result in results) {
    val fromId = moduleIndex.getOrDefault(result.contentModuleName.value, -1)
    if (fromId < 0) continue

    // Populate production edges (writtenDependencies + existingXmlModuleDependencies)
    for (depModule in result.writtenDependencies) {
      val toId = moduleIndex.getOrDefault(depModule.value, -1)
      if (toId >= 0) {
        newStore.addEdge(EDGE_CONTENT_MODULE_DEPENDS_ON, fromId, toId)
      }
    }
    // Also add existing XML deps (explicit declarations must be validated)
    for (depModule in result.existingXmlModuleDependencies) {
      val toId = moduleIndex.getOrDefault(depModule.value, -1)
      if (toId >= 0) {
        newStore.addEdge(EDGE_CONTENT_MODULE_DEPENDS_ON, fromId, toId)
      }
    }

    // Populate test edges (testDependencies - superset of prod)
    for (depModule in result.testDependencies) {
      val toId = moduleIndex.getOrDefault(depModule.value, -1)
      if (toId >= 0) {
        newStore.addEdge(EDGE_CONTENT_MODULE_DEPENDS_ON_TEST, fromId, toId)
      }
    }
  }

  // Swap store atomically
  graph.setCurrentStore(newStore.freeze())
}

private data class JpsDeps(
  val moduleDeps: Set<ContentModuleName>,
  val pluginDeps: Set<PluginId>,
  val filteredEmbeddedModuleDeps: Set<ContentModuleName>,
)

/**
 * Computes JPS dependencies for a content module with scope filtering.
 *
 * Returns both module and plugin dependencies from the graph's EDGE_TARGET_DEPENDS_ON edges.
 * Uses scope filtering to exclude TEST scope dependencies when needed.
 *
 * @param moduleName The content module name
 * @param includeTestScope If false, excludes TEST scope dependencies
 */
private fun computeJpsDeps(
  graph: PluginGraph,
  moduleName: ContentModuleName,
  includeTestScope: Boolean,
  libraryModuleFilter: (String) -> Boolean,
): JpsDeps {
  val moduleDeps = HashSet<ContentModuleName>()
  val pluginDeps = HashSet<PluginId>()
  val filteredEmbeddedModuleDeps = HashSet<ContentModuleName>()
  graph.query {
    val mod = contentModule(moduleName) ?: return JpsDeps(moduleDeps, pluginDeps, filteredEmbeddedModuleDeps)

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
            if (c.moduleName.value.startsWith(LIB_MODULE_PREFIX) && !libraryModuleFilter(c.moduleName.value)) {
              return@dependsOn
            }
            // Skip globally embedded modules - but only for content modules in plugins
            // Content modules directly in products should not skip embedded deps
            val sourceModuleId = mod.id
            val depModuleId = contentModule(c.moduleName)?.id ?: -1
            if (depModuleId >= 0 && shouldSkipEmbeddedContentDependency(sourceModuleId, depModuleId)) {
              filteredEmbeddedModuleDeps.add(c.moduleName)
            }
            else {
              moduleDeps.add(c.moduleName)
            }
          }
          is DependencyClassification.PluginDep -> pluginDeps.add(c.pluginId)
          DependencyClassification.Skip -> {}
        }
      }
    }
  }
  return JpsDeps(moduleDeps, pluginDeps, filteredEmbeddedModuleDeps)
}
