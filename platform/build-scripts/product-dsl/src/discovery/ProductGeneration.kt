// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.productLayout.discovery

import com.intellij.platform.plugins.parser.impl.LoadedXIncludeReference
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.productLayout.ModuleSet
import org.jetbrains.intellij.build.productLayout.ProductModulesContentSpec
import org.jetbrains.intellij.build.productLayout.cleanupOrphanedModuleSetFiles
import org.jetbrains.intellij.build.productLayout.dependency.ModuleDescriptorCache
import org.jetbrains.intellij.build.productLayout.dependency.generateModuleDescriptorDependencies
import org.jetbrains.intellij.build.productLayout.dependency.generatePluginDependencies
import org.jetbrains.intellij.build.productLayout.doGenerateAllModuleSetsInternal
import org.jetbrains.intellij.build.productLayout.generateProductXml
import org.jetbrains.intellij.build.productLayout.stats.GenerationResults
import org.jetbrains.intellij.build.productLayout.stats.ModuleSetFileResult
import org.jetbrains.intellij.build.productLayout.stats.ModuleSetGenerationResult
import org.jetbrains.intellij.build.productLayout.stats.ProductGenerationResult
import org.jetbrains.intellij.build.productLayout.stats.printGenerationSummary
import org.jetbrains.intellij.build.productLayout.util.AsyncCache
import org.jetbrains.intellij.build.productLayout.util.DryRunCollector
import org.jetbrains.intellij.build.productLayout.util.ValidationErrorCollector
import org.jetbrains.intellij.build.productLayout.validation.validateNoRedundantModuleSets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Configuration for generating all module sets and products.
 * Used by [generateAllModuleSetsWithProducts] to orchestrate the full generation process.
 *
 * @param moduleSetSources Map of label to (source object, output directory). The source object should contain ModuleSet functions (e.g., CommunityModuleSets, UltimateModuleSets).
 * @param discoveredProducts Products discovered from dev-build.json (must be provided by caller)
 * @param testProductSpecs List of test product specifications to generate alongside regular products
 * @param projectRoot Project root path
 * @param outputProvider Module output provider for resolving module dependencies and output directories
 */
data class ModuleSetGenerationConfig(
  @JvmField val moduleSetSources: Map<String, Pair<Any, Path>>,
  @JvmField val discoveredProducts: List<DiscoveredProduct>,
  @JvmField val testProductSpecs: List<Pair<String, ProductModulesContentSpec>> = emptyList(),
  @JvmField val projectRoot: Path,
  @JvmField val outputProvider: ModuleOutputProvider,
  @JvmField val additionalPlugins: Map<String, String> = emptyMap(),
  @JvmField val dependencyFilter: (embeddedModules: Set<String>, moduleName: String, depName: String) -> Boolean,
)

/**
 * Generates product XMLs for all products using programmatic content.
 * Takes discovered products and test product specs, then generates complete plugin.xml files.
 *
 * @param discoveredProducts Regular products discovered from dev-build.json
 * @param testProductSpecs Test product specifications (name to ProductModulesContentSpec pairs)
 * @param projectRoot The project root path
 * @param outputProvider Module output provider for resolving module dependencies
 * @return Result containing generation statistics
 */
internal suspend fun generateAllProductXmlFiles(
  discoveredProducts: List<DiscoveredProduct>,
  testProductSpecs: List<Pair<String, ProductModulesContentSpec>> = emptyList(),
  projectRoot: Path,
  outputProvider: ModuleOutputProvider,
  dryRunCollector: DryRunCollector? = null,
): ProductGenerationResult {
  // Convert test product specs to DiscoveredProduct instances
  val testProducts = testProductSpecs.mapNotNull { (name, spec) ->
    val xmlPath = "ultimate/platform-ultimate/testResources/META-INF/${name}Plugin.xml"
    val xmlFile = projectRoot.resolve(xmlPath)
    if (Files.notExists(xmlFile)) {
      return@mapNotNull null
    }

    DiscoveredProduct(
      name = name,
      config = ProductConfiguration(
        className = "test-product",
        modules = emptyList(),
        pluginXmlPath = xmlPath,
      ),
      properties = null,
      spec = spec,
      pluginXmlPath = xmlPath,
    )
  }

  val allProducts = discoveredProducts + testProducts

  // Detect if this is an Ultimate build by checking if community directory is a subdirectory
  val isUltimateBuild = Files.exists(projectRoot.resolve("community"))

  val productResults = coroutineScope {
    allProducts.map { discovered ->
      async {
        // Skip products without pluginXmlPath or spec configured
        val pluginXmlRelativePath = discovered.pluginXmlPath ?: return@async null
        val spec = discovered.spec ?: return@async null

        val pluginXmlPath = projectRoot.resolve(pluginXmlRelativePath)

        // Extract ProductProperties class name (works with both ProductProperties and null)
        val productPropertiesClass = when (val props = discovered.properties) {
          null -> "test-product"
          else -> props.javaClass.name
        }

        generateProductXml(
          pluginXmlPath = pluginXmlPath,
          spec = spec,
          productName = discovered.name,
          outputProvider = outputProvider,
          productPropertiesClass = productPropertiesClass,
          projectRoot = projectRoot,
          isUltimateBuild = isUltimateBuild,
          dryRunCollector = dryRunCollector,
        )
      }
    }.awaitAll().filterNotNull()
  }

  return ProductGenerationResult(productResults)
}

/**
 * Discovers all module sets from configured sources in parallel.
 */
private suspend fun discoverAllModuleSets(moduleSetSources: Map<String, Pair<Any, Path>>): List<ModuleSet> {
  return coroutineScope {
    moduleSetSources.map { (_, source) ->
      async {
        val (sourceObj, _) = source
        discoverModuleSets(sourceObj)
      }
    }.awaitAll().flatten()
  }
}

/**
 * Aggregates tracking maps from multiple generation results and cleans up orphaned files.
 * Returns the list of deleted file results.
 */
private fun aggregateAndCleanupOrphanedFiles(moduleSetResults: List<ModuleSetGenerationResult>): List<ModuleSetFileResult> {
  val aggregatedTrackingMap = mutableMapOf<Path, MutableSet<String>>()
  for (result in moduleSetResults) {
    for ((dir, files) in result.trackingMap) {
      aggregatedTrackingMap.computeIfAbsent(dir) { mutableSetOf() }.addAll(files)
    }
  }

  val deletedFiles = cleanupOrphanedModuleSetFiles(aggregatedTrackingMap)
  if (deletedFiles.isNotEmpty()) {
    println("\nDeleted ${deletedFiles.size} orphaned files")
  }
  return deletedFiles
}

/**
 * Generates all module sets and products with validation.
 * Base implementation that orchestrates the full generation process.
 *
 * This function:
 * 1. Discovers all module sets from configured sources
 * 2. Validates all products (using pre-discovered products from config)
 * 3. Generates module set XMLs in parallel
 * 4. Generates module dependencies and product XMLs
 * 5. Prints a comprehensive summary
 *
 * @param config Configuration specifying module set sources, discovered products, test products, and other parameters
 */
suspend fun generateAllModuleSetsWithProducts(config: ModuleSetGenerationConfig, dryRunCollector: DryRunCollector? = null, errorCollector: ValidationErrorCollector? = null) {
  val startTime = System.currentTimeMillis()

  // Discover all module sets and validate products
  val allModuleSets = discoverAllModuleSets(config.moduleSetSources)
  val products = config.discoveredProducts.map { it.name to it.spec }
  validateNoRedundantModuleSets(allModuleSets = allModuleSets, productSpecs = products)

  // Execute all generation operations in parallel
  val (moduleSetResults, dependencyResult, pluginDependencyResult, productResult) = coroutineScope {
    // Compute embedded modules once (deferred), used by both TIER 2 and TIER 3
    val embeddedModulesDeferred = async {
      collectEmbeddedModulesFromProducts(config.discoveredProducts)
    }

    // TIER 1: Parallel module set generation for all configured sources
    val moduleSetJobs = config.moduleSetSources.map { (label, source) ->
      val (sourceObj, outputDir) = source
      async {
        doGenerateAllModuleSetsInternal(
          obj = sourceObj,
          outputDir = outputDir,
          label = label,
          outputProvider = config.outputProvider,
          dryRunCollector = dryRunCollector,
        )
      }
    }

    // Collect all bundled plugins and launch content extraction jobs ONCE
    // multiple consumers can await these Deferred values (validation + plugin dep gen)
    val allBundledPlugins = (config.discoveredProducts.asSequence().mapNotNull { it.spec?.bundledPlugins }.flatten() + config.additionalPlugins.keys)
      .distinct()
      .toList()

    val xIncludeCache = AsyncCache<String, LoadedXIncludeReference?>(this)
    val pluginContentJobs: Map<String, Deferred<PluginContentInfo?>> = allBundledPlugins.associateWith { pluginName ->
      async {
        extractPluginContent(pluginName = pluginName, outputProvider = config.outputProvider, xIncludeCache = xIncludeCache)
      }
    }

    // TIER 2: Parallel dependency and product generation (can run concurrently with TIER 1)
    val dependencyJob = async {
      val moduleSetsByLabel = config.moduleSetSources.mapValues { (_, source) ->
        val (sourceObj, _) = source
        discoverModuleSets(sourceObj)
      }

      val cache = ModuleDescriptorCache(config.outputProvider, this)
      generateModuleDescriptorDependencies(
        communityModuleSets = moduleSetsByLabel.get("community") ?: emptyList(),
        ultimateModuleSets = moduleSetsByLabel.get("ultimate") ?: emptyList(),
        coreModuleSets = moduleSetsByLabel.get("core") ?: emptyList(),
        cache = cache,
        productSpecs = products,
        pluginContentJobs = pluginContentJobs,
        additionalPlugins = config.additionalPlugins,
        errorCollector = errorCollector,
      )
    }

    // TIER 3: Plugin dependency generation for bundled plugins
    val pluginDependencyJob = async {
      if (allBundledPlugins.isEmpty()) {
        null
      }
      else {
        val cache = ModuleDescriptorCache(config.outputProvider, this)
        val embeddedModules = embeddedModulesDeferred.await()
        generatePluginDependencies(
          plugins = allBundledPlugins,
          pluginContentJobs = pluginContentJobs,
          descriptorCache = cache,
          dependencyFilter = { moduleName, depName -> config.dependencyFilter(embeddedModules, moduleName, depName) },
        )
      }
    }

    val productJob = async {
      generateAllProductXmlFiles(
        discoveredProducts = config.discoveredProducts,
        testProductSpecs = config.testProductSpecs,
        projectRoot = config.projectRoot,
        outputProvider = config.outputProvider,
        dryRunCollector = dryRunCollector,
      )
    }

    GenerationResults(
      moduleSetResults = moduleSetJobs.awaitAll(),
      dependencyResult = dependencyJob.await(),
      pluginDependencyResult = pluginDependencyJob.await(),
      productResult = productJob.await(),
    )
  }

  aggregateAndCleanupOrphanedFiles(moduleSetResults)

  printGenerationSummary(
    moduleSetResults = moduleSetResults,
    dependencyResult = dependencyResult,
    pluginDependencyResult = pluginDependencyResult,
    productResult = productResult,
    projectRoot = config.projectRoot,
    durationMs = System.currentTimeMillis() - startTime,
  )
}
