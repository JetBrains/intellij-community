// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment", "GrazieInspection")

package org.jetbrains.intellij.build.productLayout.dependency

import com.intellij.platform.buildScripts.concurrency.SharedCache
import com.intellij.platform.buildScripts.concurrency.SharedTaskOwner
import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.PluginGraph
import com.intellij.platform.pluginGraph.PluginId
import com.intellij.platform.pluginGraph.TargetName
import com.intellij.platform.pluginGraph.contentName
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.productLayout.config.SuppressionConfig
import org.jetbrains.intellij.build.productLayout.config.ValidationException
import org.jetbrains.intellij.build.productLayout.deps.ContentModuleDependencyPlan
import org.jetbrains.intellij.build.productLayout.deps.ContentModuleDependencyPlanOutput
import org.jetbrains.intellij.build.productLayout.discovery.PluginContentInfo
import org.jetbrains.intellij.build.productLayout.generator.PluginGraphDeps
import org.jetbrains.intellij.build.productLayout.generator.buildActionGroupProviderModules
import org.jetbrains.intellij.build.productLayout.generator.collectPluginGraphDeps
import org.jetbrains.intellij.build.productLayout.generator.computeActionGroupModuleDependencies
import org.jetbrains.intellij.build.productLayout.generator.computeAliasPreservedPluginDeps
import org.jetbrains.intellij.build.productLayout.generator.computeExistingDependencyHandling
import org.jetbrains.intellij.build.productLayout.generator.filterPluginDependencies
import org.jetbrains.intellij.build.productLayout.generator.planContentModuleDependenciesWithBothSets
import org.jetbrains.intellij.build.productLayout.generator.updateGraphWithModuleDependencyPlans
import org.jetbrains.intellij.build.productLayout.model.ErrorSink
import org.jetbrains.intellij.build.productLayout.model.error.ErrorCategory
import org.jetbrains.intellij.build.productLayout.pipeline.ContentModuleOutput
import org.jetbrains.intellij.build.productLayout.pipeline.DataSlot
import org.jetbrains.intellij.build.productLayout.pipeline.Slots
import org.jetbrains.intellij.build.productLayout.stats.DependencyFileResult
import org.jetbrains.intellij.build.productLayout.stats.FileChangeStatus
import org.jetbrains.intellij.build.productLayout.stats.PluginDependencyFileResult
import org.jetbrains.intellij.build.productLayout.stats.PluginDependencyGenerationResult
import org.jetbrains.intellij.build.productLayout.util.FileUpdateStrategy
import org.jetbrains.intellij.build.productLayout.util.withUpdateSuppressions
import org.jetbrains.intellij.build.productLayout.validator.ContentModulePluginDependencyValidator
import org.jetbrains.intellij.build.productLayout.validator.PluginContentDependencyValidator
import org.jetbrains.intellij.build.productLayout.xml.extractDependenciesEntries
import org.jetbrains.intellij.build.productLayout.xml.updateXmlDependencies
import org.jetbrains.intellij.build.mapConcurrent

/**
 * Simplified entry point for tests - extension on [PluginTestSetupContext].
 *
 * Reduces boilerplate by using the setup's fields automatically:
 * ```kotlin
 * coroutineScope {
 *   setup.generateDependencies(listOf("plugin.name"))
 * }
 * ```
 *
 * For more control, use [generatePluginDependencies] directly.
 */
internal fun PluginTestSetupContext.generateDependencies(
  plugins: List<String>,
  suppressionConfig: SuppressionConfig = SuppressionConfig(),
  testFrameworkContentModules: Set<ContentModuleName> = emptySet(),
  pluginAllowedMissingDependencies: Map<TargetName, Set<ContentModuleName>> = emptyMap(),
  contentModuleAllowedMissingPluginDeps: Map<ContentModuleName, Set<PluginId>> = emptyMap(),
  productAllowedMissing: Map<String, Set<ContentModuleName>> = emptyMap(),
  updateSuppressions: Boolean = false,
): PluginDependencyGenerationResult {
  return SharedTaskOwner("plugin dependency test").use { owner ->
    val descriptorCache = ModuleDescriptorCache(jps.outputProvider, owner)
    generatePluginDependencies(
      plugins = plugins,
      pluginContentCache = pluginContentCache,
      testSetup = this@generateDependencies,
      graph = pluginGraph,
      descriptorCache = descriptorCache,
      suppressionConfig = suppressionConfig,
      updateSuppressions = updateSuppressions,
      strategy = strategy,
      testFrameworkContentModules = testFrameworkContentModules,
      pluginAllowedMissingDependencies = pluginAllowedMissingDependencies,
      contentModuleAllowedMissingPluginDeps = contentModuleAllowedMissingPluginDeps,
      productAllowedMissing = productAllowedMissing,
    )
  }
}

/**
 * Test-friendly entry point for plugin dependency generation.
 *
 * This function provides a simplified API for tests that bypasses the pipeline
 * architecture. For production use, prefer
 * [org.jetbrains.intellij.build.productLayout.generator.ContentModuleDependencyPlanner],
 * [org.jetbrains.intellij.build.productLayout.generator.ContentModuleXmlWriter],
 * [org.jetbrains.intellij.build.productLayout.generator.PluginDependencyPlanner],
 * [org.jetbrains.intellij.build.productLayout.generator.PluginXmlWriter], and
 * [org.jetbrains.intellij.build.productLayout.validator.PluginContentDependencyValidator] and
 * [org.jetbrains.intellij.build.productLayout.validator.ContentModulePluginDependencyValidator].
 */
internal fun generatePluginDependencies(
  plugins: List<String>,
  pluginContentCache: PluginContentProvider,
  testSetup: PluginTestSetupContext,
  graph: PluginGraph,
  descriptorCache: ModuleDescriptorCache,
  suppressionConfig: SuppressionConfig,
  strategy: FileUpdateStrategy,
  testFrameworkContentModules: Set<ContentModuleName>,
  pluginAllowedMissingDependencies: Map<TargetName, Set<ContentModuleName>> = emptyMap(),
  contentModuleAllowedMissingPluginDeps: Map<ContentModuleName, Set<PluginId>> = emptyMap(),
  productAllowedMissing: Map<String, Set<ContentModuleName>> = emptyMap(),
  updateSuppressions: Boolean = false,
): PluginDependencyGenerationResult {
  return run {
    if (plugins.isEmpty()) {
      return@run PluginDependencyGenerationResult(emptyList())
    }

    val outputProvider = testSetup.jps.outputProvider
    val owner = descriptorCache.owner
    val contentModuleCache = SharedCache<String, PlannedContentModuleResult?>(owner)
    val pluginGraphDeps = collectPluginGraphDeps(graph = graph)
      .associateBy { it.pluginContentModuleName.value }
    val actionGroupProviderModules = buildActionGroupProviderModules(graph = graph, descriptorCache = descriptorCache)

    val generationOutputs = plugins.mapConcurrent { pluginModuleName ->
      run {
        val graphDeps = pluginGraphDeps.get(pluginModuleName) ?: return@mapConcurrent null
        generatePluginDependency(
          pluginModuleName = TargetName(pluginModuleName),
          graphDeps = graphDeps,
          pluginContentCache = pluginContentCache,
          graph = graph,
          outputProvider = outputProvider,
          descriptorCache = descriptorCache,
          actionGroupProviderModules = actionGroupProviderModules,
          suppressionConfig = suppressionConfig,
          updateSuppressions = updateSuppressions,
          strategy = strategy,
          contentModuleCache = contentModuleCache,
        )
      }
    }.filterNotNull()

    val generationResults = generationOutputs.map { it.fileResult }

    // Rebuild graph with testFrameworkContentModules for test plugin detection
    val effectiveGraph = buildPluginGraphFromTestSetup(
      plugins = emptyList(),  // Not needed - we use knownPlugins
      products = testSetup.products,
      knownPlugins = testSetup.pluginContentCache.getKnownPlugins(),
      testFrameworkContentModules = testFrameworkContentModules,
      contentModuleSpecs = testSetup.contentModuleSpecs,
    )

    // Populate content module dependency edges on the graph (mirrors ContentModuleDependencyNode)
    // This is needed for validation to query deps via EDGE_CONTENT_MODULE_DEPENDS_ON
    val allContentModuleResults = generationResults.flatMap { it.contentModuleResults }
    val allContentModulePlans = generationOutputs.flatMap { it.contentModulePlans }
    val deduplicatedPlans = allContentModulePlans
      .associateBy { it.contentModuleName }
      .values
      .toList()
    updateGraphWithModuleDependencyPlans(effectiveGraph, deduplicatedPlans)

    val validationCache = buildValidationCache(
      owner = owner,
      outputProvider = outputProvider,
      pluginContentInfos = testSetup.pluginContentInfos,
    )
    val validationExceptions = contentModuleAllowedMissingPluginDeps.mapValues { (_, plugins) ->
      ValidationException(allowMissingPlugins = plugins)
    }
    val validationConfig = if (validationExceptions.isEmpty()) {
      SuppressionConfig()
    }
    else {
      SuppressionConfig(validationExceptions = validationExceptions)
    }
    val pluginAllowedMissingByModule = pluginAllowedMissingDependencies.mapKeys { ContentModuleName(it.key.value) }
    val validationModel = testGenerationModel(
      owner = owner,
      pluginGraph = effectiveGraph,
      outputProvider = outputProvider,
      fileUpdater = testSetup.strategy,
      pluginContentCache = validationCache,
      suppressionConfig = validationConfig,
      pluginAllowedMissingDependencies = pluginAllowedMissingByModule,
      productAllowedMissing = productAllowedMissing,
    )
    val slotOverrides = mapOf<DataSlot<*>, Any>(
      Slots.CONTENT_MODULE to ContentModuleOutput(files = allContentModuleResults),
      Slots.CONTENT_MODULE_PLAN to ContentModuleDependencyPlanOutput(deduplicatedPlans),
    )
    val pluginErrors = runValidationRule(
      PluginContentDependencyValidator,
      validationModel,
      slotOverrides = slotOverrides,
    )
    val contentModulePluginErrors = runValidationRule(
      ContentModulePluginDependencyValidator,
      validationModel,
      slotOverrides = slotOverrides,
    )
    val filteredErrors = pluginErrors + contentModulePluginErrors

    PluginDependencyGenerationResult(generationResults, filteredErrors)
  }
}

private data class PlannedContentModuleResult(
  val plan: ContentModuleDependencyPlan,
  val result: DependencyFileResult,
)

private data class PluginDependencyGenerationOutput(
  val fileResult: PluginDependencyFileResult,
  val contentModulePlans: List<ContentModuleDependencyPlan>,
)

// ========== Private helpers ==========

private fun generatePluginDependency(
  pluginModuleName: TargetName,
  graphDeps: PluginGraphDeps,
  pluginContentCache: PluginContentProvider,
  graph: PluginGraph,
  outputProvider: ModuleOutputProvider,
  descriptorCache: ModuleDescriptorCache,
  actionGroupProviderModules: Map<String, Set<ContentModuleName>>,
  suppressionConfig: SuppressionConfig,
  updateSuppressions: Boolean,
  strategy: FileUpdateStrategy,
  contentModuleCache: SharedCache<String, PlannedContentModuleResult?>,
): PluginDependencyGenerationOutput? {
  val info = pluginContentCache.getOrExtract(pluginModuleName) ?: return null
  val effectiveStrategy = strategy.withUpdateSuppressions(updateSuppressions)

  // For DSL-defined plugins, no filtering (empty suppression)
  val effectiveConfig = if (graphDeps.isDslDefined) SuppressionConfig() else suppressionConfig

  val pluginContentModuleName = graphDeps.pluginContentModuleName
  val existingXmlModuleDeps = info.moduleDependencies
  val existingXmlPluginDeps: Set<PluginId> = info.depsByFile.firstOrNull()?.pluginDependencies ?: emptySet()
  val mainDependencyEntries = extractDependenciesEntries(info.pluginXmlContent)
  val managedXmlModuleDeps = mainDependencyEntries?.managedModuleNames?.mapTo(HashSet(), ::ContentModuleName) ?: existingXmlModuleDeps
  val managedXmlPluginDeps = mainDependencyEntries?.managedPluginIds?.mapTo(HashSet(), ::PluginId) ?: existingXmlPluginDeps
  val actionGroupModuleDeps = computeActionGroupModuleDependencies(
    pluginInfo = info,
    graphDeps = graphDeps,
    actionGroupProviderModules = actionGroupProviderModules,
  )
  val effectiveJpsModuleDependencies = graphDeps.jpsModuleDependencies + actionGroupModuleDeps
  val effectiveGraphDeps = graphDeps.copy(jpsModuleDependencies = effectiveJpsModuleDependencies)
  val effectiveJpsPluginDependencies = graphDeps.jpsPluginDependencies - graphDeps.legacyConfigFilePluginDependencies
  val suppressedModules = effectiveConfig.getPluginSuppressedModules(pluginContentModuleName)
  val suppressedPlugins = effectiveConfig.getPluginSuppressedPlugins(pluginContentModuleName)
  val moduleHandling = computeExistingDependencyHandling(
    updateSuppressions = updateSuppressions,
    existingXmlDeps = existingXmlModuleDeps,
    jpsDeps = effectiveJpsModuleDependencies,
    suppressedDeps = suppressedModules,
    xmlOnlySuppressionCandidateDeps = managedXmlModuleDeps,
  )
  val pluginHandling = computeExistingDependencyHandling(
    updateSuppressions = updateSuppressions,
    existingXmlDeps = existingXmlPluginDeps,
    jpsDeps = effectiveJpsPluginDependencies,
    suppressedDeps = suppressedPlugins,
    semanticallyPreservedExistingDeps = computeAliasPreservedPluginDeps(graph, existingXmlPluginDeps),
    xmlOnlySuppressionCandidateDeps = managedXmlPluginDeps,
  )
  val effectiveSuppressedModules = moduleHandling.effectiveSuppressedDeps
  val effectiveSuppressedPlugins = pluginHandling.effectiveSuppressedDeps

  val deps = filterPluginDependencies(
    graphDeps = effectiveGraphDeps,
    pluginInfo = info,
    jpsPluginDependencies = effectiveJpsPluginDependencies,
    suppressedModules = effectiveSuppressedModules,
    suppressedPlugins = effectiveSuppressedPlugins,
  )

  val status = updateXmlDependencies(
    path = info.pluginXmlPath,
    content = info.pluginXmlContent,
    moduleDependencies = deps.moduleDependencies.map { it.value },
    pluginDependencies = deps.pluginDependencies.map { it.value },
    preserveExistingModule = { moduleName -> ContentModuleName(moduleName) in moduleHandling.preserveExistingDeps },
    preserveExistingPlugin = { pluginName -> PluginId(pluginName) in pluginHandling.preserveExistingDeps },
    strategy = effectiveStrategy,
  )

  val contentModuleResults = mutableListOf<DependencyFileResult>()
  val contentModulePlans = mutableListOf<ContentModuleDependencyPlan>()
  for (module in info.contentModules) {
    val contentModule = module.moduleId.contentName()
    val contentModuleName = contentModule.value

    // Use production function for content module dependency generation
    // Tests pass through their SuppressionConfig (same as production)
    val planned = contentModuleCache.getOrPut(contentModuleName) {
      val generation = planContentModuleDependenciesWithBothSets(
        contentModuleName = contentModule,
        descriptorCache = descriptorCache,
        outputProvider = outputProvider,
        pluginGraph = graph,
        suppressionConfig = effectiveConfig,
        updateSuppressions = updateSuppressions,
      )
      val plan = generation.plan ?: return@getOrPut null
      PlannedContentModuleResult(plan = plan, result = writeContentModulePlan(plan, effectiveStrategy))
    }
    if (planned != null) {
      contentModuleResults.add(planned.result)
      contentModulePlans.add(planned.plan)
    }
  }

  return PluginDependencyGenerationOutput(
    fileResult = PluginDependencyFileResult(
      pluginContentModuleName = ContentModuleName(pluginModuleName.value),
      pluginXmlPath = info.pluginXmlPath,
      status = status,
      dependencyCount = deps.moduleDependencies.size + deps.pluginDependencies.size,
      contentModuleResults = contentModuleResults,
    ),
    contentModulePlans = contentModulePlans,
  )
}

private fun writeContentModulePlan(plan: ContentModuleDependencyPlan, strategy: FileUpdateStrategy): DependencyFileResult {
  if (plan.suppressibleError?.category == ErrorCategory.NON_STANDARD_DESCRIPTOR_ROOT) {
    return DependencyFileResult(
      contentModuleName = plan.contentModuleName,
      descriptorPath = plan.descriptorPath,
      status = FileChangeStatus.UNCHANGED,
      writtenDependencies = emptyList(),
      testDependencies = emptyList(),
      existingXmlModuleDependencies = emptySet(),
      writtenPluginDependencies = emptyList(),
      requiredPluginDependencies = emptySet(),
      suppressionUsages = emptyList(),
    )
  }

  val status = updateXmlDependencies(
    path = plan.descriptorPath,
    content = plan.descriptorContent,
    moduleDependencies = plan.moduleDependencies.map { it.value },
    pluginDependencies = plan.pluginDependencies.map { it.value },
    preserveExistingModule = { moduleName -> plan.suppressedModules.contains(ContentModuleName(moduleName)) },
    preserveExistingPlugin = { pluginName -> plan.preserveExistingPluginDependencies.contains(PluginId(pluginName)) },
    strategy = strategy,
  )

  return DependencyFileResult(
    contentModuleName = plan.contentModuleName,
    descriptorPath = plan.descriptorPath,
    status = status,
    writtenDependencies = plan.moduleDependencies,
    testDependencies = plan.testDependencies,
    existingXmlModuleDependencies = plan.existingXmlModuleDependencies,
    writtenPluginDependencies = plan.writtenPluginDependencies,
    requiredPluginDependencies = plan.requiredPluginDependencies,
    suppressionUsages = plan.suppressionUsages,
  )
}

private fun buildValidationCache(
  owner: SharedTaskOwner,
  outputProvider: ModuleOutputProvider,
  pluginContentInfos: Map<String, PluginContentInfo>,
): PluginContentCache {
  val cache = PluginContentCache(
    outputProvider = outputProvider,
    xIncludeCache = SharedCache(owner),
    skipXIncludePaths = emptySet(),
    xIncludePrefixFilter = { null },
    errorSink = ErrorSink(),
  )
  for ((moduleName, info) in pluginContentInfos) {
    cache.addDslTestPlugin(TargetName(moduleName), info)
  }
  return cache
}
