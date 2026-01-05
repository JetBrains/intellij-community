// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.productLayout.discovery

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.productLayout.ModuleSet
import org.jetbrains.intellij.build.productLayout.ProductModulesContentSpec
import org.jetbrains.intellij.build.productLayout.cleanupOrphanedModuleSetFiles
import org.jetbrains.intellij.build.productLayout.dependency.AllPluginModules
import org.jetbrains.intellij.build.productLayout.dependency.ModuleDescriptorCache
import org.jetbrains.intellij.build.productLayout.dependency.generateModuleDescriptorDependencies
import org.jetbrains.intellij.build.productLayout.dependency.generatePluginDependencies
import org.jetbrains.intellij.build.productLayout.doGenerateAllModuleSetsInternal
import org.jetbrains.intellij.build.productLayout.generateProductXml
import org.jetbrains.intellij.build.productLayout.stats.DependencyGenerationResult
import org.jetbrains.intellij.build.productLayout.stats.GenerationStats
import org.jetbrains.intellij.build.productLayout.stats.ModuleSetFileResult
import org.jetbrains.intellij.build.productLayout.stats.ModuleSetGenerationResult
import org.jetbrains.intellij.build.productLayout.stats.PluginDependencyGenerationResult
import org.jetbrains.intellij.build.productLayout.stats.ProductGenerationResult
import org.jetbrains.intellij.build.productLayout.traversal.ModuleSetTraversalCache
import org.jetbrains.intellij.build.productLayout.util.AsyncCache
import org.jetbrains.intellij.build.productLayout.util.DeferredFileUpdater
import org.jetbrains.intellij.build.productLayout.util.FileUpdateStrategy
import org.jetbrains.intellij.build.productLayout.validation.FileDiff
import org.jetbrains.intellij.build.productLayout.validation.ProductModuleIndex
import org.jetbrains.intellij.build.productLayout.validation.ValidationError
import org.jetbrains.intellij.build.productLayout.validation.rules.buildAllProductIndices
import org.jetbrains.intellij.build.productLayout.validation.rules.validateNoRedundantModuleSets
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
  /**
   * Map of product name to set of plugins that are compatible with (installable) but not bundled in that product.
   * Loaded from packaging test YAMLs (nonBundled field).
   * Used to track plugin compatibility for error message formatting.
   */
  @JvmField val nonBundledPlugins: Map<String, Set<String>> = emptyMap(),
  /**
   * Set of plugin names that are known to exist but not associated with any specific product.
   * Used for validation purposes only (to recognize these plugins as valid dependency targets).
   */
  @JvmField val knownPlugins: Set<String> = emptySet(),
  /**
   * Set of test plugin names that have their plugin.xml in test resources.
   * These plugins need special handling during content extraction (onlyProductionSources = false).
   */
  @JvmField val testPlugins: Set<String> = emptySet(),
  /**
   * Filter to determine which dependencies should be included in generated XML files.
   *
   * @param embeddedModules Set of modules that are embedded in the product
   * @param moduleName The module whose dependencies are being processed
   * @param depName The dependency module name being considered
   * @param isTest Whether this is for a test descriptor (`._test.xml`) or production descriptor
   */
  @JvmField val dependencyFilter: (embeddedModules: Set<String>, moduleName: String, depName: String, isTest: Boolean) -> Boolean,
  /** Xi:include paths to skip during plugin content extraction (e.g., paths from external libraries like Kotlin compiler) */
  @JvmField val skipXIncludePaths: Set<String> = emptySet(),
  /** Returns prefix for xi:include filtering, or null to disable filtering for this module */
  @JvmField val xIncludePrefixFilter: (moduleName: String) -> String? = { null },
  /**
   * Modules that indicate a plugin is a test plugin when declared as content.
   * Plugins declaring any of these as `<content>` are excluded from production validation
   * because they won't be present at runtime.
   */
  @JvmField val testFrameworkContentModules: Set<String> = emptySet(),
  /**
   * Library names that are considered testing libraries.
   * Modules in product distributions should have these libraries in 'test' scope only.
   * If a module has these libraries in production scope, a diff will be generated to fix it.
   */
  @JvmField val testingLibraries: Set<String> = emptySet(),
  /**
   * Map of plugin module name to set of allowed missing dependencies.
   * Used to suppress validation errors for plugin dependencies that are intentionally missing
   * (e.g., bundled via withProjectLibrary or other mechanisms not visible to the validator).
   */
  @JvmField val pluginAllowedMissingDependencies: Map<String, Set<String>> = emptyMap(),
  /**
   * Filter to control which library modules should replace library references in .iml files.
   * When a library is exported by a library module (e.g., Guava exported by intellij.libraries.guava),
   * this filter determines whether the library reference should be replaced with a module reference.
   *
   * @param libraryModuleName The library module name (e.g., "intellij.libraries.guava")
   * @return true if the library should be replaced with a module dependency, false to keep the library reference
   */
  @JvmField val libraryModuleFilter: (libraryModuleName: String) -> Boolean = { true },
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
  strategy: DeferredFileUpdater,
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
          strategy = strategy,
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
private fun aggregateAndCleanupOrphanedFiles(moduleSetResults: List<ModuleSetGenerationResult>, strategy: FileUpdateStrategy): List<ModuleSetFileResult> {
  val aggregatedTrackingMap = mutableMapOf<Path, MutableSet<String>>()
  for (result in moduleSetResults) {
    for ((dir, files) in result.trackingMap) {
      aggregatedTrackingMap.computeIfAbsent(dir) { mutableSetOf() }.addAll(files)
    }
  }

  val deletedFiles = cleanupOrphanedModuleSetFiles(aggregatedTrackingMap, strategy)
  if (deletedFiles.isNotEmpty()) {
    println("\nDeleted ${deletedFiles.size} orphaned files")
  }
  return deletedFiles
}

/**
 * Result of generation including errors and diffs.
 * Diffs are available for validation/dry-run mode.
 */
/** Intermediate results from parallel generation jobs (before durationMs is computed) */
private data class IntermediateResults(
  val moduleSetResults: List<ModuleSetGenerationResult>,
  val dependencyResult: DependencyGenerationResult,
  val pluginDependencyResult: PluginDependencyGenerationResult?,
  val productResult: ProductGenerationResult,
)

data class GenerationResult(
  @JvmField val errors: List<ValidationError>,
  @JvmField val diffs: List<FileDiff>,
  @JvmField val stats: GenerationStats,
) {
  /** Combined list of all validation issues (errors + diffs) */
  val allIssues: List<ValidationError>
    get() = errors + diffs
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
 * Uses deferred writes - files are only written to disk if validation passes.
 *
 * @param config Configuration specifying module set sources, discovered products, test products, and other parameters
 * @param commitChanges If true, commits writes to disk when validation passes. If false, returns diffs without writing.
 * @return Result containing validation errors and diffs
 */
suspend fun generateAllModuleSetsWithProducts(
  config: ModuleSetGenerationConfig,
  commitChanges: Boolean = true,
): GenerationResult {
  val startTime = System.currentTimeMillis()

  // Use deferred strategy to avoid writing invalid files on validation failure
  val strategy = DeferredFileUpdater(config.projectRoot)

  // Discover all module sets and validate products
  val allModuleSets = discoverAllModuleSets(config.moduleSetSources)
  val products = config.discoveredProducts.map { it.name to it.spec }
  validateNoRedundantModuleSets(allModuleSets = allModuleSets, productSpecs = products)

  // Execute all generation operations in parallel
  val generationResults = coroutineScope {
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
          strategy = strategy,
        )
      }
    }

    // Collect all bundled plugins and launch content extraction jobs ONCE
    // multiple consumers can await these Deferred values (validation + plugin dep gen)
    // Collect all plugins: bundled from products + nonBundled from all products + known plugins
    val allNonBundledPlugins = config.nonBundledPlugins.values.asSequence().flatten()
    // testPlugins are automatically included as known plugins (they need content extraction too)
    val allBundledPlugins = (config.discoveredProducts.asSequence().mapNotNull { it.spec?.bundledPlugins }.flatten() + allNonBundledPlugins + config.knownPlugins + config.testPlugins)
      .distinct()
      .toList()

    val xIncludeCache = AsyncCache<String, ByteArray?>(this)
    val pluginContentJobs: Map<String, Deferred<PluginContentInfo?>> = allBundledPlugins.associateWith { pluginName ->
      async {
        extractPluginContent(
          pluginName = pluginName,
          outputProvider = config.outputProvider,
          xIncludeCache = xIncludeCache,
          skipXIncludePaths = config.skipXIncludePaths,
          prefixFilter = config.xIncludePrefixFilter,
          onlyProductionSources = pluginName !in config.testPlugins,
        )
      }
    }

    val cache = ModuleDescriptorCache(outputProvider = config.outputProvider, scope = this)

    // Compute module sets once - shared by TIER 2 and TIER 3
    val moduleSetsByLabel = config.moduleSetSources.mapValues { (_, source) ->
      val (sourceObj, _) = source
      discoverModuleSets(sourceObj)
    }
    val traversalCache = ModuleSetTraversalCache(moduleSetsByLabel.values.flatten())

    // Compute all plugin modules once as Deferred - used by TIER 2 validation and TIER 3 plugin deps
    // Single pass builds both flat set (for validation) and by-plugin map (for test plugin filtering)
    val allPluginModulesDeferred = async {
      val allModules = HashSet<String>()
      val byPlugin = HashMap<String, Set<String>>()
      for ((pluginName, job) in pluginContentJobs) {
        val contentModules = job.await()?.contentModules ?: continue
        allModules.addAll(contentModules)
        byPlugin[pluginName] = contentModules
      }
      AllPluginModules(allModules, byPlugin)
    }

    // Compute product indices once as Deferred - shared by TIER 2 validation and TIER 3 plugin dep validation
    val productIndicesDeferred: Deferred<Map<String, ProductModuleIndex>> = async {
      buildAllProductIndices(products, traversalCache, pluginContentJobs)
    }

    // TIER 2: Parallel dependency and product generation (can run concurrently with TIER 1)
    val dependencyJob = async {
      generateModuleDescriptorDependencies(
        communityModuleSets = moduleSetsByLabel.get("community") ?: emptyList(),
        ultimateModuleSets = moduleSetsByLabel.get("ultimate") ?: emptyList(),
        coreModuleSets = moduleSetsByLabel.get("core") ?: emptyList(),
        cache = cache,
        productSpecs = products,
        pluginContentJobs = pluginContentJobs,
        allPluginModulesDeferred = allPluginModulesDeferred,
        productIndicesDeferred = productIndicesDeferred,
        nonBundledPlugins = config.nonBundledPlugins,
        knownPlugins = config.knownPlugins,
        testingLibraries = config.testingLibraries,
        libraryModuleFilter = config.libraryModuleFilter,
        strategy = strategy,
      )
    }

    // TIER 3: Plugin dependency generation for bundled plugins
    val pluginDependencyJob = async {
      if (allBundledPlugins.isEmpty()) {
        null
      }
      else {
        val embeddedModules = embeddedModulesDeferred.await()

        generatePluginDependencies(
          plugins = allBundledPlugins,
          pluginContentJobs = pluginContentJobs,
          allPluginModulesDeferred = allPluginModulesDeferred,
          productIndicesDeferred = productIndicesDeferred,
          descriptorCache = cache,
          dependencyFilter = { moduleName, depName, isTest -> config.dependencyFilter(embeddedModules, moduleName, depName, isTest) },
          strategy = strategy,
          testFrameworkContentModules = config.testFrameworkContentModules,
          pluginAllowedMissingDependencies = config.pluginAllowedMissingDependencies,
        )
      }
    }

    val productJob = async {
      generateAllProductXmlFiles(
        discoveredProducts = config.discoveredProducts,
        testProductSpecs = config.testProductSpecs,
        projectRoot = config.projectRoot,
        outputProvider = config.outputProvider,
        strategy = strategy,
      )
    }

    IntermediateResults(
      moduleSetResults = moduleSetJobs.awaitAll(),
      dependencyResult = dependencyJob.await(),
      pluginDependencyResult = pluginDependencyJob.await(),
      productResult = productJob.await(),
    )
  }

  val errors = generationResults.dependencyResult.errors + (generationResults.pluginDependencyResult?.errors ?: emptyList())

  // Only commit writes if validation passed and caller requested commits
  if (errors.isEmpty() && commitChanges) {
    aggregateAndCleanupOrphanedFiles(generationResults.moduleSetResults, strategy)
    strategy.commit()
  }

  val stats = GenerationStats(
    moduleSetResults = generationResults.moduleSetResults,
    dependencyResult = generationResults.dependencyResult,
    pluginDependencyResult = generationResults.pluginDependencyResult,
    productResult = generationResults.productResult,
    durationMs = System.currentTimeMillis() - startTime,
  )

  // Get all diffs for display in packaging tests
  val allDiffs = strategy.getDiffs()
  return GenerationResult(errors = errors, diffs = allDiffs, stats = stats)
}
