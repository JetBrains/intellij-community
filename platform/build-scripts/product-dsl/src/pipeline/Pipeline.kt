// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package org.jetbrains.intellij.build.productLayout.pipeline

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jetbrains.intellij.build.productLayout.cleanupOrphanedModuleSetFiles
import org.jetbrains.intellij.build.productLayout.discovery.GenerationResult
import org.jetbrains.intellij.build.productLayout.discovery.ModuleSetGenerationConfig
import org.jetbrains.intellij.build.productLayout.generator.ContentModuleDependencyPlanner
import org.jetbrains.intellij.build.productLayout.generator.ContentModuleXmlWriter
import org.jetbrains.intellij.build.productLayout.generator.ModuleSetXmlGenerator
import org.jetbrains.intellij.build.productLayout.generator.PluginDependencyPlanner
import org.jetbrains.intellij.build.productLayout.generator.PluginXmlWriter
import org.jetbrains.intellij.build.productLayout.generator.ProductModuleDependencyGenerator
import org.jetbrains.intellij.build.productLayout.generator.ProductXmlGenerator
import org.jetbrains.intellij.build.productLayout.generator.SuppressionConfigGenerator
import org.jetbrains.intellij.build.productLayout.generator.TestPluginDependencyPlanner
import org.jetbrains.intellij.build.productLayout.generator.TestPluginXmlGenerator
import org.jetbrains.intellij.build.productLayout.model.ErrorSink
import org.jetbrains.intellij.build.productLayout.model.error.FileDiff
import org.jetbrains.intellij.build.productLayout.model.error.ValidationError
import org.jetbrains.intellij.build.productLayout.stats.DependencyGenerationResult
import org.jetbrains.intellij.build.productLayout.stats.GenerationStats
import org.jetbrains.intellij.build.productLayout.stats.ModuleSetFileResult
import org.jetbrains.intellij.build.productLayout.stats.ModuleSetGenerationResult
import org.jetbrains.intellij.build.productLayout.stats.PluginDependencyGenerationResult
import org.jetbrains.intellij.build.productLayout.stats.ProductGenerationResult
import org.jetbrains.intellij.build.productLayout.stats.SuppressionConfigStats
import org.jetbrains.intellij.build.productLayout.stats.TestPluginGenerationResult
import org.jetbrains.intellij.build.productLayout.validator.ContentModuleBackingValidator
import org.jetbrains.intellij.build.productLayout.validator.ContentModuleDependencyValidator
import org.jetbrains.intellij.build.productLayout.validator.ContentModulePluginDependencyValidator
import org.jetbrains.intellij.build.productLayout.validator.LibraryModuleValidator
import org.jetbrains.intellij.build.productLayout.validator.PluginContentDependencyValidator
import org.jetbrains.intellij.build.productLayout.validator.PluginContentDuplicatesValidator
import org.jetbrains.intellij.build.productLayout.validator.PluginContentStructureValidator
import org.jetbrains.intellij.build.productLayout.validator.PluginDependencyDeclarationValidator
import org.jetbrains.intellij.build.productLayout.validator.PluginDescriptorIdConflictValidator
import org.jetbrains.intellij.build.productLayout.validator.PluginPluginDependencyValidator
import org.jetbrains.intellij.build.productLayout.validator.ProductModuleSetValidator
import org.jetbrains.intellij.build.productLayout.validator.SelfContainedModuleSetValidator
import org.jetbrains.intellij.build.productLayout.validator.SuppressionConfigValidator
import org.jetbrains.intellij.build.productLayout.validator.TestLibraryScopeValidator
import org.jetbrains.intellij.build.productLayout.validator.TestPluginPluginDependencyValidator
import java.nio.file.Path

/**
 * Orchestrates the product-dsl generation pipeline.
 *
 * The pipeline consists of 5 stages:
 * 1. **DISCOVER** - Scan DSL definitions for module sets and products
 * 2. **BUILD_MODEL** - Create caches and compute shared values
 * 3. **EXECUTE** - Run compute nodes in dependency order (respecting slot dependencies)
 * 4. **AGGREGATE** - Collect errors, diffs, and stats from context
 * 5. **OUTPUT** - Commit changes or return diffs
 *
 * **Slot-based execution:**
 * Nodes declare what slots they [PipelineNode.requires] (read) and [PipelineNode.produces] (write).
 * The pipeline infers execution order from these declarations - no explicit `runAfter` needed.
 *
 * **Usage:**
 * ```kotlin
 * val pipeline = GenerationPipeline.default()
 * val result = pipeline.execute(config, commitChanges = true)
 * ```
 *
 * **Benefits:**
 * - Clear data flow through typed slots
 * - Execution order inferred from slot dependencies
 * - Parallel execution where dependencies allow
 * - Unified error handling through ComputeContext
 */
internal class GenerationPipeline(
  private val nodes: List<PipelineNode>,
) {
  /** Map from slot to the node that produces it */
  private val slotProducers: Map<DataSlot<*>, PipelineNode>

  init {
    // Build slot → producer mapping and validate
    val producers = HashMap<DataSlot<*>, PipelineNode>()
    for (node in nodes) {
      for (slot in node.produces) {
        val existing = producers.put(slot, node)
        require(existing == null) {
          "Slot '${slot.name}' produced by multiple nodes: '${existing!!.id.name}' and '${node.id.name}'"
        }
      }
    }
    slotProducers = producers

    validateNodes()
  }

  /**
   * Validates node configuration at construction time.
   * - Checks for duplicate IDs
   * - Checks for missing slot producers (required slots with no producer)
   * - Checks for circular dependencies
   */
  private fun validateNodes() {
    val ids = nodes.map { it.id }
    val duplicates = ids.groupingBy { it }.eachCount().filter { it.value > 1 }
    require(duplicates.isEmpty()) {
      "Duplicate node IDs: ${duplicates.keys.map { it.name }}"
    }

    // Check that all required slots have producers (excluding ErrorSlots)
    for (node in nodes) {
      for (slot in node.requires) {
        if (slot is ErrorSlot) continue // Error slots are auto-populated
        require(slot in slotProducers) {
          "Node '${node.id.name}' requires slot '${slot.name}' but no node produces it"
        }
      }
    }

    // Check for circular dependencies via topological sort
    topologicalSort() // Will throw if cycle detected
  }

  /**
   * Executes the full generation pipeline.
   *
   * @param config Generation configuration
   * @param commitChanges If true, writes files when validation passes. If false, returns diffs only.
   * @param updateSuppressions If true, updates suppressions.json (no XML changes)
   * @param validationFilter If non-null, only runs validation nodes with matching names.
   *        Generation nodes always run. Pass empty set to skip all validation.
   * @return Result with errors, diffs, and statistics
   */
  suspend fun execute(
    config: ModuleSetGenerationConfig,
    commitChanges: Boolean = true,
    updateSuppressions: Boolean = false,
    validationFilter: Set<String>? = null,
  ): GenerationResult {
    val startTime = System.currentTimeMillis()

    return coroutineScope {
      // Stage 1: DISCOVER - Scan DSL definitions
      val discovery = discover(config)

      // Stage 2: BUILD_MODEL - Create caches and compute shared values
      // ModelBuildingStage has its own ErrorSink for xi:include errors discovered during extraction
      val modelBuildingErrorSink = ErrorSink()
      val model = ModelBuildingStage.execute(
        discovery = discovery,
        config = config,
        scope = this,
        updateSuppressions = updateSuppressions,
        commitChanges = commitChanges,
        errorSink = modelBuildingErrorSink
      )

      // Stage 3: EXECUTE - Run compute nodes in dependency order
      val ctx = executeNodes(model, validationFilter)

      // Stage 4: AGGREGATE - Collect errors, diffs, and tracking maps
      val aggregated = aggregate(ctx, model, modelBuildingErrorSink)

      // Stage 5: OUTPUT - Cleanup orphans and commit or return diffs
      val deletedFiles = output(aggregated.errors, model, commitChanges, aggregated.trackingMaps)

      // Build final stats including deleted files (after cleanup)
      val stats = buildStats(ctx, System.currentTimeMillis() - startTime, deletedFiles, model.fileUpdater.getDiffs())

      GenerationResult(errors = aggregated.errors, diffs = aggregated.diffs, stats = stats)
    }
  }

  // ============ Stage 1: DISCOVER ============

  /**
   * Stage 1: Discovers module sets and products from DSL definitions.
   *
   * Delegates to [DiscoveryStage] for actual implementation.
   */
  private suspend fun discover(config: ModuleSetGenerationConfig): DiscoveryResult {
    return DiscoveryStage.execute(config)
  }

  // ============ Stage 2: EXECUTE ============

  /**
   * Stage 3: Executes compute nodes in dependency order with maximum parallelism.
   *
   * Uses slot dependencies to determine execution order. Nodes with no required slots
   * run immediately (level 0). A node starts when all its required slots are published.
   *
   * @param model The shared generation model
   * @param validationFilter If non-null, only runs validation nodes with matching names.
   *        Generation nodes always run. Pass empty set to skip all validation.
   * @return The compute context containing all slot values and errors
   */
  private suspend fun executeNodes(
    model: GenerationModel,
    validationFilter: Set<String>?,
  ): ComputeContextImpl {
    return coroutineScope {
      // Filter nodes based on validationFilter
      val activeNodes = nodes.filter { node ->
        node.id.category != NodeCategory.VALIDATION ||
        validationFilter == null ||
        node.id.name in validationFilter
      }

      // Create context and initialize all slots
      val ctx = ComputeContextImpl(model)
      initializeSlots(ctx, activeNodes)

      val sorted = topologicalSort(activeNodes)

      // Group nodes by "level" (nodes at same level can run in parallel)
      val levels = computeLevels(sorted)

      for (level in levels) {
        // Run all nodes at this level in parallel
        level.map { node ->
          async {
            val nodeCtx = ctx.forNode(node.id)
            node.execute(nodeCtx)
            ctx.finalizeNodeErrors(node.id)
          }
        }.awaitAll()
      }

      ctx
    }
  }

  /**
   * Initializes all slots in the context before execution.
   * - Data slots from node.produces
   * - Error slots for all nodes
   */
  private fun initializeSlots(ctx: ComputeContextImpl, activeNodes: List<PipelineNode>) {
    // Initialize data slots
    for (node in activeNodes) {
      for (slot in node.produces) {
        ctx.initSlot(slot)
      }
    }
    // Initialize error slots for all nodes
    for (node in activeNodes) {
      ctx.initSlot(ErrorSlot(node.id))
      ctx.initErrorSlot(node.id)
    }
  }

  /**
   * Computes execution levels for parallel execution.
   * Nodes at the same level have no dependencies on each other.
   */
  private fun computeLevels(sorted: List<PipelineNode>): List<List<PipelineNode>> {
    val levels = ArrayList<ArrayList<PipelineNode>>()
    val nodeLevels = HashMap<NodeId, Int>()

    for (node in sorted) {
      // Find max level of dependencies
      val depLevel = node.requires.maxOfOrNull { slot ->
        when (slot) {
          is ErrorSlot -> nodeLevels.get(slot.generatorId) ?: 0
          else -> slotProducers.get(slot)?.let { nodeLevels.get(it.id) } ?: 0
        }
      } ?: -1

      val myLevel = depLevel + 1
      nodeLevels.put(node.id, myLevel)

      while (levels.size <= myLevel) {
        levels.add(ArrayList())
      }
      levels.get(myLevel).add(node)
    }

    return levels
  }

  /**
   * Topological sort of nodes based on slot dependencies.
   * @param nodesToSort List of nodes to sort (maybe filtered)
   * @throws IllegalStateException if circular dependency detected
   */
  private fun topologicalSort(nodesToSort: List<PipelineNode> = nodes): List<PipelineNode> {
    val result = ArrayList<PipelineNode>()
    val visited = HashSet<NodeId>()
    val visiting = HashSet<NodeId>() // For cycle detection
    val nodeMap = nodesToSort.associateBy { it.id }

    fun visit(node: PipelineNode) {
      if (node.id in visited) return
      if (node.id in visiting) {
        error("Circular dependency detected involving node '${node.id}'")
      }

      visiting.add(node.id)

      // Visit dependencies (nodes that produce required slots)
      for (slot in node.requires) {
        val depNode = when (slot) {
          is ErrorSlot -> nodeMap.get(slot.generatorId)
          else -> slotProducers.get(slot)?.let { nodeMap.get(it.id) }
        }
        if (depNode != null) {
          visit(depNode)
        }
      }

      visiting.remove(node.id)
      visited.add(node.id)
      result.add(node)
    }

    for (node in nodesToSort) {
      visit(node)
    }

    return result
  }

  // ============ Stage 4: AGGREGATE ============

  /**
   * Intermediate result from aggregation stage.
   */
  private data class AggregatedResult(
    val errors: List<ValidationError>,
    val diffs: List<FileDiff>,
    /** Tracking maps for orphan cleanup: directory → generated file names */
    val trackingMaps: Map<Path, Set<String>>,
  )

  /**
   * Stage 4: Aggregates results from context.
   *
   * Collects:
   * - Validation errors from model building (xi:include errors) and all nodes
   * - File diffs from slot outputs and file updater
   * - Tracking maps for orphan file cleanup
   *
   * Errors with non-null [ValidationError.suppressionKey] are filtered through
   * the suppression config. Suppressed errors are excluded from the final list.
   *
   * Note: Statistics are built in Stage 5 after cleanup (to include deleted files).
   */
  private fun aggregate(
    ctx: ComputeContextImpl,
    model: GenerationModel,
    modelBuildingErrorSink: ErrorSink,
  ): AggregatedResult {
    val allDiffs = ArrayList<FileDiff>()

    // Collect diffs from slot outputs
    ctx.tryGet(Slots.MODULE_SETS)?.diffs?.let { allDiffs.addAll(it) }
    ctx.tryGet(Slots.PRODUCT_MODULE_DEPS)?.diffs?.let { allDiffs.addAll(it) }
    ctx.tryGet(Slots.CONTENT_MODULE)?.diffs?.let { allDiffs.addAll(it) }
    ctx.tryGet(Slots.PLUGIN_XML)?.diffs?.let { allDiffs.addAll(it) }
    ctx.tryGet(Slots.PRODUCTS)?.diffs?.let { allDiffs.addAll(it) }
    ctx.tryGet(Slots.TEST_PLUGINS)?.diffs?.let { allDiffs.addAll(it) }
    ctx.tryGet(Slots.SUPPRESSION_CONFIG)?.diffs?.let { allDiffs.addAll(it) }

    // Add diffs from file updater
    allDiffs.addAll(model.fileUpdater.getDiffs())

    // Collect all errors: model building + all nodes
    // Filter through suppression config
    val allErrors = (modelBuildingErrorSink.getErrors() + ctx.getAllErrorsFlat())
      .filter { error ->
        val key = error.suppressionKey
        key == null || !model.suppressionConfig.isSuppressed(key)
      }

    // Aggregate tracking maps from module set output
    val aggregatedTrackingMaps = HashMap<Path, MutableSet<String>>()
    ctx.tryGet(Slots.MODULE_SETS)?.let { moduleSets ->
      for ((dir, files) in moduleSets.trackingMaps) {
        aggregatedTrackingMaps.computeIfAbsent(dir) { HashSet() }.addAll(files)
      }
    }

    return AggregatedResult(
      errors = allErrors,
      diffs = allDiffs,
      trackingMaps = aggregatedTrackingMaps,
    )
  }

  /**
   * Builds generation statistics from context slot values.
   *
   * @param ctx The compute context with slot values
   * @param durationMs Total duration in milliseconds
   * @param deletedFiles Files deleted during orphan cleanup (added to module set results)
   */
  private fun buildStats(
    ctx: ComputeContextImpl,
    durationMs: Long,
    deletedFiles: List<ModuleSetFileResult> = emptyList(),
    fileUpdaterDiffs: List<FileDiff> = emptyList(),
  ): GenerationStats {
    // Read from slots
    val moduleSetsOutput = ctx.tryGet(Slots.MODULE_SETS)
    val productModuleDepsOutput = ctx.tryGet(Slots.PRODUCT_MODULE_DEPS)
    val contentModuleOutput = ctx.tryGet(Slots.CONTENT_MODULE)
    val pluginXmlOutput = ctx.tryGet(Slots.PLUGIN_XML)
    val productsOutput = ctx.tryGet(Slots.PRODUCTS)
    val testPluginsOutput = ctx.tryGet(Slots.TEST_PLUGINS)
    val suppressionConfigOutput = ctx.tryGet(Slots.SUPPRESSION_CONFIG)

    // Get errors for specific nodes
    val productModuleDepErrors = ctx.getNodeErrors(NodeIds.PRODUCT_MODULE_DEPS)
    val pluginValidationErrors = ctx.getNodeErrors(NodeIds.PLUGIN_VALIDATION) +
      ctx.getNodeErrors(NodeIds.PLUGIN_CONTENT_STRUCTURE_VALIDATION) +
      ctx.getNodeErrors(NodeIds.CONTENT_MODULE_PLUGIN_DEPENDENCY_VALIDATION) +
      ctx.getNodeErrors(NodeIds.PLUGIN_PLUGIN_VALIDATION) +
      ctx.getNodeErrors(NodeIds.PLUGIN_DEPENDENCY_DECLARATION_VALIDATION)

    // Build module set results grouped by label
    val moduleSetResults = moduleSetsOutput?.let { output ->
      output.resultsByLabel.map { labelResult ->
        ModuleSetGenerationResult(
          label = labelResult.label,
          outputDir = labelResult.outputDir,
          files = labelResult.files + deletedFiles.filter { it.fileName.startsWith("intellij.moduleSets") },
          trackingMap = labelResult.trackingMap,
        )
      }
    } ?: emptyList()

    return GenerationStats(
      moduleSetResults = moduleSetResults,
      dependencyResult = productModuleDepsOutput?.let {
        DependencyGenerationResult(files = it.files, errors = productModuleDepErrors, diffs = it.diffs)
      },
      contentModuleResult = contentModuleOutput?.let {
        DependencyGenerationResult(files = it.files, diffs = it.diffs)
      },
      pluginDependencyResult = pluginXmlOutput?.let {
        PluginDependencyGenerationResult(files = it.files, errors = pluginValidationErrors)
      },
      productResult = productsOutput?.let {
        ProductGenerationResult(products = it.files)
      },
      testPluginResult = testPluginsOutput?.let {
        TestPluginGenerationResult(plugins = it.files)
      },
      suppressionConfigStats = suppressionConfigOutput?.let {
        SuppressionConfigStats(
          moduleCount = it.moduleCount,
          suppressionCount = it.suppressionCount,
          staleCount = it.staleCount,
          configModified = it.configModified,
        )
      },
      durationMs = durationMs,
      fileUpdaterDiffs = fileUpdaterDiffs,
    )
  }

  // ============ Stage 5: OUTPUT ============

  /**
   * Stage 5: Commits changes or returns without writing.
   *
   * When committing:
   * 1. Cleans up orphaned module set files (files no longer generated by DSL)
   * 2. Commits all deferred `file writes` atomically
   */
  private fun output(
    errors: List<ValidationError>,
    model: GenerationModel,
    commitChanges: Boolean,
    trackingMaps: Map<Path, Set<String>>,
  ): List<ModuleSetFileResult> {
    if (errors.isEmpty() && commitChanges) {
      // Clean up orphaned module set files before commit
      val deletedFiles = cleanupOrphanedModuleSetFiles(trackingMaps, model.xmlWritePolicy)
      if (deletedFiles.isNotEmpty()) {
        println("\nDeleted ${deletedFiles.size} orphaned files")
      }

      // Commit all deferred writes atomically
      model.fileUpdater.commit()

      return deletedFiles
    }
    return emptyList()
  }

  companion object {
    /**
     * Creates a pipeline with the default set of compute nodes.
     *
     * Execution order is inferred from [PipelineNode.requires] and [PipelineNode.produces].
     * Nodes with no requirements run first (level 0). Nodes run as soon as their
     * required slots are published.
     */
    fun default(): GenerationPipeline {
      return GenerationPipeline(
        nodes = listOf(
          ModuleSetXmlGenerator,
          ProductModuleDependencyGenerator,
          ContentModuleDependencyPlanner,
          ContentModuleXmlWriter,
          PluginDependencyPlanner,
          PluginXmlWriter,
          PluginContentDependencyValidator,
          PluginContentStructureValidator,
          ContentModulePluginDependencyValidator,
          PluginPluginDependencyValidator,
          PluginDependencyDeclarationValidator,
          ProductXmlGenerator,
          TestPluginDependencyPlanner,
          TestPluginXmlGenerator,
          TestPluginPluginDependencyValidator,
          SuppressionConfigGenerator,
          SuppressionConfigValidator,
          TestLibraryScopeValidator,
          SelfContainedModuleSetValidator,
          ContentModuleBackingValidator,
          ProductModuleSetValidator,
          PluginContentDuplicatesValidator,
          PluginDescriptorIdConflictValidator,
          ContentModuleDependencyValidator,
          LibraryModuleValidator,
        )
      )
    }
  }
}
