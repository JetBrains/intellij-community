// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment", "GrazieInspection")

package org.jetbrains.intellij.build.productLayout.dependency

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.DependencyClassification
import com.intellij.platform.pluginGraph.PluginGraph
import com.intellij.platform.pluginGraph.PluginId
import com.intellij.platform.pluginGraph.TargetName
import com.intellij.platform.pluginGraph.isSlashNotation
import com.intellij.platform.pluginSystem.parser.impl.parseContentAndXIncludes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.findFileInModuleSources
import org.jetbrains.intellij.build.productLayout.config.SuppressionConfig
import org.jetbrains.intellij.build.productLayout.config.ValidationException
import org.jetbrains.intellij.build.productLayout.deps.ContentModuleDependencyPlan
import org.jetbrains.intellij.build.productLayout.deps.ContentModuleDependencyPlanOutput
import org.jetbrains.intellij.build.productLayout.discovery.PluginContentInfo
import org.jetbrains.intellij.build.productLayout.generator.PluginGraphDeps
import org.jetbrains.intellij.build.productLayout.generator.collectPluginGraphDeps
import org.jetbrains.intellij.build.productLayout.generator.computeEffectiveSuppressedDeps
import org.jetbrains.intellij.build.productLayout.generator.embeddedCheckProductNames
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
import org.jetbrains.intellij.build.productLayout.util.AsyncCache
import org.jetbrains.intellij.build.productLayout.util.FileUpdateStrategy
import org.jetbrains.intellij.build.productLayout.util.withUpdateSuppressions
import org.jetbrains.intellij.build.productLayout.validator.ContentModulePluginDependencyValidator
import org.jetbrains.intellij.build.productLayout.validator.PluginContentDependencyValidator
import org.jetbrains.intellij.build.productLayout.xml.updateXmlDependencies
import java.nio.file.Files

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
internal suspend fun PluginTestSetupContext.generateDependencies(
  plugins: List<String>,
  suppressionConfig: SuppressionConfig = SuppressionConfig(),
  testFrameworkContentModules: Set<ContentModuleName> = emptySet(),
  pluginAllowedMissingDependencies: Map<TargetName, Set<ContentModuleName>> = emptyMap(),
  contentModuleAllowedMissingPluginDeps: Map<ContentModuleName, Set<PluginId>> = emptyMap(),
  productAllowedMissing: Map<String, Set<ContentModuleName>> = emptyMap(),
  updateSuppressions: Boolean = false,
): PluginDependencyGenerationResult {
  return coroutineScope {
    val descriptorCache = ModuleDescriptorCache(jps.outputProvider, this)
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
internal suspend fun generatePluginDependencies(
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
  return coroutineScope {
    if (plugins.isEmpty()) {
      return@coroutineScope PluginDependencyGenerationResult(emptyList())
    }

    val outputProvider = testSetup.jps.outputProvider
    val contentModuleCache = AsyncCache<String, PlannedContentModuleResult?>(this)
    val testContentModuleCache = AsyncCache<String, DependencyFileResult?>(this)
    val allRealProductNames = embeddedCheckProductNames(testSetup.products.map { it.name })
    val pluginGraphDeps = collectPluginGraphDeps(
      graph = graph,
      allRealProductNames = allRealProductNames,
      libraryModuleFilter = { true },
    )
      .associateBy { it.pluginContentModuleName.value }

    val generationOutputs = plugins.map { pluginModuleName ->
      async {
        val graphDeps = pluginGraphDeps.get(pluginModuleName) ?: return@async null
        generatePluginDependency(
          pluginModuleName = TargetName(pluginModuleName),
          graphDeps = graphDeps,
          pluginContentCache = pluginContentCache,
          graph = graph,
          allRealProductNames = allRealProductNames,
          outputProvider = outputProvider,
          descriptorCache = descriptorCache,
          suppressionConfig = suppressionConfig,
          updateSuppressions = updateSuppressions,
          strategy = strategy,
          contentModuleCache = contentModuleCache,
          testContentModuleCache = testContentModuleCache,
        )
      }
    }.awaitAll().filterNotNull()

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
      outputProvider = outputProvider,
      pluginContentInfos = testSetup.pluginContentInfos,
      scope = this,
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

private suspend fun generatePluginDependency(
  pluginModuleName: TargetName,
  graphDeps: PluginGraphDeps,
  pluginContentCache: PluginContentProvider,
  graph: PluginGraph,
  allRealProductNames: Set<String>,
  outputProvider: ModuleOutputProvider,
  descriptorCache: ModuleDescriptorCache,
  suppressionConfig: SuppressionConfig,
  updateSuppressions: Boolean,
  strategy: FileUpdateStrategy,
  contentModuleCache: AsyncCache<String, PlannedContentModuleResult?>,
  testContentModuleCache: AsyncCache<String, DependencyFileResult?>,
): PluginDependencyGenerationOutput? {
  val info = pluginContentCache.getOrExtract(pluginModuleName) ?: return null
  val effectiveStrategy = strategy.withUpdateSuppressions(updateSuppressions)

  // For DSL-defined plugins, no filtering (empty suppression)
  val effectiveConfig = if (graphDeps.isDslDefined) SuppressionConfig() else suppressionConfig

  val pluginContentModuleName = graphDeps.pluginContentModuleName
  val existingXmlModuleDeps = info.moduleDependencies
  val existingXmlPluginDeps: Set<PluginId> = info.depsByFile.firstOrNull()?.pluginDependencies ?: emptySet()
  val effectiveJpsPluginDependencies = graphDeps.jpsPluginDependencies - graphDeps.legacyConfigFilePluginDependencies
  val suppressedModules = effectiveConfig.getPluginSuppressedModules(pluginContentModuleName)
  val suppressedPlugins = effectiveConfig.getPluginSuppressedPlugins(pluginContentModuleName)
  val effectiveSuppressedModules = computeEffectiveSuppressedDeps(
    updateSuppressions = updateSuppressions,
    existingXmlDeps = existingXmlModuleDeps,
    jpsDeps = graphDeps.jpsModuleDependencies,
    suppressedDeps = suppressedModules,
  )
  val effectiveSuppressedPlugins = computeEffectiveSuppressedDeps(
    updateSuppressions = updateSuppressions,
    existingXmlDeps = existingXmlPluginDeps,
    jpsDeps = effectiveJpsPluginDependencies,
    suppressedDeps = suppressedPlugins,
  )

  val deps = filterPluginDependencies(
    graphDeps = graphDeps,
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
    preserveExistingModule = { moduleName -> ContentModuleName(moduleName) in effectiveSuppressedModules },
    preserveExistingPlugin = { pluginName -> PluginId(pluginName) in effectiveSuppressedPlugins },
    strategy = effectiveStrategy,
  )

  val contentModuleResults = mutableListOf<DependencyFileResult>()
  val contentModulePlans = mutableListOf<ContentModuleDependencyPlan>()
  for (module in info.contentModules) {
    val contentModuleName = module.name.value
    val isTestModule = contentModuleName.endsWith("._test")

    // Use production function for content module dependency generation
    // Tests pass through their SuppressionConfig (same as production)
    val planned = contentModuleCache.getOrPut(contentModuleName) {
      val generation = planContentModuleDependenciesWithBothSets(
        contentModuleName = module.name,
        descriptorCache = descriptorCache,
        pluginGraph = graph,
        allRealProductNames = allRealProductNames,
        isTestDescriptor = isTestModule,
        suppressionConfig = effectiveConfig,
        updateSuppressions = updateSuppressions,
        libraryModuleFilter = { true },
      )
      val plan = generation.plan ?: return@getOrPut null
      PlannedContentModuleResult(plan = plan, result = writeContentModulePlan(plan, effectiveStrategy))
    }
    if (planned != null) {
      contentModuleResults.add(planned.result)
      contentModulePlans.add(planned.plan)
    }

    if (!isTestModule) {
      val testResult = testContentModuleCache.getOrPut(contentModuleName) {
        // Compute deps using graph
        val graphModuleDeps = HashSet<ContentModuleName>()
        graph.query {
          val mod = contentModule(ContentModuleName(contentModuleName)) ?: return@query
          mod.backedBy { target ->
            target.dependsOn { dep ->
              when (val c = classifyTarget(dep.targetId)) {
                is DependencyClassification.ModuleDep -> graphModuleDeps.add(c.moduleName)
                else -> {}
              }
            }
          }
        }

        val testSuppressedModules = planned?.plan?.suppressedModules ?: effectiveConfig.getSuppressedModules(module.name)
        generateTestDescriptorDependencies(
          contentModuleName = module.name,
          outputProvider = outputProvider,
          graphModuleDeps = graphModuleDeps,
          dependencyFilter = { depName -> !testSuppressedModules.contains(ContentModuleName(depName)) },
          strategy = effectiveStrategy,
        )
      }
      if (testResult != null) {
        contentModuleResults.add(testResult)
      }
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
      allJpsPluginDependencies = emptySet(),
      suppressionUsages = emptyList(),
    )
  }

  val status = updateXmlDependencies(
    path = plan.descriptorPath,
    content = plan.descriptorContent,
    moduleDependencies = plan.moduleDependencies.map { it.value },
    pluginDependencies = plan.pluginDependencies.map { it.value },
    preserveExistingModule = { moduleName -> plan.suppressedModules.contains(ContentModuleName(moduleName)) },
    preserveExistingPlugin = { pluginName -> plan.suppressedPlugins.contains(PluginId(pluginName)) },
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
    allJpsPluginDependencies = plan.allJpsPluginDependencies,
    suppressionUsages = plan.suppressionUsages,
  )
}

/**
 * Generates dependencies for a test descriptor file (moduleName._test.xml).
 *
 * Test descriptor files provide additional test-time dependencies for content modules.
 * They are separate from the main module descriptor (`moduleName.xml`).
 *
 * Note: This is different from "test descriptor modules" (`foo._test` content modules).
 * This function generates `foo._test.xml` for regular `foo` modules.
 *
 * @param contentModuleName The base module name (without ._test suffix)
 * @param outputProvider JPS output provider for locating test descriptor files
 * @param graphModuleDeps Pre-computed module dependencies from the graph
 * @param dependencyFilter Filter that returns true for deps to INCLUDE (false = suppressed)
 * @param strategy File update strategy (write vs diff)
 * @return Result with written dependencies or null if no test descriptor exists
 */
private suspend fun generateTestDescriptorDependencies(
  contentModuleName: ContentModuleName,
  outputProvider: ModuleOutputProvider,
  graphModuleDeps: Set<ContentModuleName>,
  dependencyFilter: (String) -> Boolean,
  strategy: FileUpdateStrategy,
): DependencyFileResult? {
  // Handle slash-notation modules (e.g., "intellij.restClient/intelliLang")
  // These are virtual content modules without separate JPS modules - no test descriptors
  if (contentModuleName.isSlashNotation()) {
    return null
  }

  val jpsModule = outputProvider.findRequiredModule(contentModuleName.value)
  val descriptorPath = findFileInModuleSources(
    module = jpsModule,
    relativePath = "${contentModuleName.value}._test.xml",
    onlyProductionSources = false,
  ) ?: return null

  val content = withContext(Dispatchers.IO) { Files.readString(descriptorPath) }
  if (content.contains("@skip-dependency-generation")) {
    return null
  }

  // Parse existing XML dependencies for accurate implicitDependencies computation
  val parseResult = parseContentAndXIncludes(input = content.toByteArray(), locationSource = null)
  val existingModuleDeps = parseResult.moduleDependencies.toSet()

  val moduleDeps = mutableListOf<String>()
  for (depModule in graphModuleDeps) {
    val depName = depModule.value
    if (dependencyFilter(depName)) {
      moduleDeps.add(depName)
    }
  }

  val status = updateXmlDependencies(
    path = descriptorPath,
    content = content,
    moduleDependencies = moduleDeps.distinct().sorted(),
    pluginDependencies = emptyList(),
    preserveExistingModule = { !dependencyFilter(it) },
    preserveExistingPlugin = { _ -> true },
    strategy = strategy,
  )
  return DependencyFileResult(
    contentModuleName = ContentModuleName("${contentModuleName.value}._test"),
    // Explicit: test descriptor ._test gets deps from the base JPS module (without suffix)
    sourceJpsModule = contentModuleName,
    descriptorPath = descriptorPath,
    status = status,
    writtenDependencies = moduleDeps.distinct().sorted().map(::ContentModuleName),
    existingXmlModuleDependencies = existingModuleDeps.mapTo(HashSet(), ::ContentModuleName),
  )
}

private suspend fun buildValidationCache(
  outputProvider: ModuleOutputProvider,
  pluginContentInfos: Map<String, PluginContentInfo>,
  scope: CoroutineScope,
): PluginContentCache {
  val cache = PluginContentCache(
    outputProvider = outputProvider,
    xIncludeCache = AsyncCache(scope),
    skipXIncludePaths = emptySet(),
    xIncludePrefixFilter = { null },
    scope = scope,
    errorSink = ErrorSink(),
  )
  for ((moduleName, info) in pluginContentInfos) {
    cache.addDslTestPlugin(TargetName(moduleName), info)
  }
  return cache
}
