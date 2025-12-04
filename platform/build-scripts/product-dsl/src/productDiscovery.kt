// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import java.nio.file.Files
import java.nio.file.Path

/**
 * Path to the product registry JSON file.
 */
const val PRODUCT_REGISTRY_PATH: String = "build/dev-build.json"

/**
 * Product registry containing all product configurations from dev-build.json.
 */
@Serializable
data class ProductConfigurationRegistry(@JvmField val products: Map<String, ProductConfiguration>)

/**
 * Product configuration from dev-build.json.
 */
@Serializable
data class ProductConfiguration(
  @JvmField val modules: List<String>,
  @JvmField @SerialName("class") val className: String,
  @JvmField val pluginXmlPath: String? = null
)

/**
 * Represents a discovered product with all its metadata.
 * Used by both XML and JSON generators to avoid code duplication.
 * Test products have properties = null (they don't have ProductProperties classes).
 *
 * Note: The `properties` field uses type `Any?` instead of `ProductProperties?` to avoid
 * depending on the full build-scripts module (would create circular dependency).
 * In practice, it holds ProductProperties instances or null for test products.
 */
data class DiscoveredProduct(
  @JvmField val name: String,
  @JvmField val config: ProductConfiguration,
  @JvmField val properties: Any?, // ProductProperties or null
  @JvmField val spec: ProductModulesContentSpec?,
  @JvmField val pluginXmlPath: String?,
)

/**
 * Map of class FQN to actual file name for cases where class name != file name.
 * Example: DotnetExternalProductProperties class is in ReSharperExternalProductProperties.kt file.
 */
private val CLASS_TO_FILE_NAME_OVERRIDES = mapOf(
  "com.jetbrains.rider.build.product.DotnetExternalProductProperties" to "ReSharperExternalProductProperties.kt",
  "org.jetbrains.intellij.build.AndroidStudioWithMarketplaceProperties" to "UltimateAwareIdeaCommunityProperties.kt",
)

/**
 * Finds the actual source file for a ProductProperties class by searching JPS module source roots.
 * This replaces hardcoded path mapping with actual file system lookup.
 *
 * @param buildModules List of build module names from dev-build.json (e.g., ["intellij.goland.build"])
 * @param productPropertiesClass The ProductProperties class to find
 * @param moduleOutputProvider Provider for accessing JPS modules
 * @param projectRoot Project root path for making paths relative
 * @return Relative path to the source file
 */
fun findProductPropertiesSourceFile(
  buildModules: List<String>,
  productPropertiesClass: Class<*>,
  moduleOutputProvider: ModuleOutputProvider,
  projectRoot: Path
): String {
  val className = productPropertiesClass.name

  // Handle special cases where class name != file name
  val fileName = CLASS_TO_FILE_NAME_OVERRIDES[className] ?: "${className.substringAfterLast('.')}.kt"
  val packagePath = className.substringBeforeLast('.').replace('.', '/')
  val relativePath = "$packagePath/$fileName"

  // Search each build module's source roots
  for (buildModuleName in buildModules) {
    val jpsModule = moduleOutputProvider.findModule(buildModuleName) ?: continue

    // Search production source roots (not test roots)
    val sourceFile = jpsModule.sourceRoots
      .asSequence()
      .filter { it.rootType == JavaSourceRootType.SOURCE }
      .firstNotNullOfOrNull { sourceRoot ->
        JpsJavaExtensionService.getInstance().findSourceFile(sourceRoot, relativePath)
      }

    if (sourceFile != null) {
      return projectRoot.relativize(sourceFile).toString()
    }
  }

  throw IllegalStateException("Cannot find source file for $productPropertiesClass (searched for $relativePath in modules: $buildModules)")
}

/**
 * Extracts product validation data from discovered products.
 * Returns list of (productName, ProductModulesContentSpec) pairs for validation.
 *
 * @param discoveredProducts List of discovered products
 * @return List of product name and spec pairs for validation
 */
fun extractProductsForValidation(discoveredProducts: List<DiscoveredProduct>): List<Pair<String, ProductModulesContentSpec?>> {
  return discoveredProducts.map { it.name to it.spec }
}

/**
 * Generates product XMLs for all products using programmatic content.
 * Takes discovered products and test product specs, then generates complete plugin.xml files.
 *
 * @param discoveredProducts Regular products discovered from dev-build.json
 * @param testProductSpecs Test product specifications (name to ProductModulesContentSpec pairs)
 * @param projectRoot The project root path
 * @param moduleOutputProvider Module output provider for resolving module dependencies
 * @return Result containing generation statistics
 */
suspend fun generateAllProductXmlFiles(
  discoveredProducts: List<DiscoveredProduct>,
  testProductSpecs: List<Pair<String, ProductModulesContentSpec>> = emptyList(),
  projectRoot: Path,
  moduleOutputProvider: ModuleOutputProvider,
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
          moduleOutputProvider = moduleOutputProvider,
          productPropertiesClass = productPropertiesClass,
          projectRoot = projectRoot,
          isUltimateBuild = isUltimateBuild,
        )
      }
    }.awaitAll().filterNotNull()
  }

  return ProductGenerationResult(productResults)
}

/**
 * Configuration for generating all module sets and products.
 * Used by [generateAllModuleSetsWithProducts] to orchestrate the full generation process.
 *
 * @param moduleSetSources Map of label to (source object, output directory). The source object should contain ModuleSet functions (e.g., CommunityModuleSets, UltimateModuleSets).
 * @param discoveredProducts Products discovered from dev-build.json (must be provided by caller)
 * @param testProductSpecs List of test product specifications to generate alongside regular products
 * @param projectRoot Project root path
 * @param moduleOutputProvider Module output provider for resolving module dependencies and output directories
 */
data class ModuleSetGenerationConfig(
  @JvmField val moduleSetSources: Map<String, Pair<Any, Path>>,
  @JvmField val discoveredProducts: List<DiscoveredProduct>,
  @JvmField val testProductSpecs: List<Pair<String, ProductModulesContentSpec>> = emptyList(),
  @JvmField val projectRoot: Path,
  @JvmField val moduleOutputProvider: ModuleOutputProvider,
)

/**
 * Discovers all module sets from configured sources.
 */
private fun discoverAllModuleSets(moduleSetSources: Map<String, Pair<Any, Path>>): List<ModuleSet> {
  val allModuleSets = mutableListOf<ModuleSet>()
  for ((_, source) in moduleSetSources) {
    val (sourceObj, _) = source
    allModuleSets.addAll(discoverModuleSets(sourceObj))
  }
  return allModuleSets
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
suspend fun generateAllModuleSetsWithProducts(config: ModuleSetGenerationConfig) {
  val startTime = System.currentTimeMillis()

  // Discover all module sets and validate products
  val allModuleSets = discoverAllModuleSets(config.moduleSetSources)
  val products = extractProductsForValidation(config.discoveredProducts)
  validateNoRedundantModuleSets(allModuleSets = allModuleSets, productSpecs = products)

  // Execute all generation operations in parallel
  val (moduleSetResults, dependencyResult, productResult) = coroutineScope {
    // TIER 1: Parallel module set generation for all configured sources
    val moduleSetJobs = config.moduleSetSources.map { (label, source) ->
      val (sourceObj, outputDir) = source
      async {
        doGenerateAllModuleSetsInternal(
          obj = sourceObj,
          outputDir = outputDir,
          label = label,
          moduleOutputProvider = config.moduleOutputProvider
        )
      }
    }

    // TIER 2: Parallel dependency and product generation (can run concurrently with TIER 1)
    val dependencyJob = async {
      val moduleSetsByLabel = config.moduleSetSources.mapValues { (_, source) ->
        val (sourceObj, _) = source
        discoverModuleSets(sourceObj)
      }

      generateModuleDescriptorDependencies(
        communityModuleSets = moduleSetsByLabel["community"] ?: emptyList(),
        ultimateModuleSets = moduleSetsByLabel["ultimate"] ?: emptyList(),
        coreModuleSets = moduleSetsByLabel["core"] ?: emptyList(),
        moduleOutputProvider = config.moduleOutputProvider,
        productSpecs = products
      )
    }

    val productJob = async {
      generateAllProductXmlFiles(
        discoveredProducts = config.discoveredProducts,
        testProductSpecs = config.testProductSpecs,
        projectRoot = config.projectRoot,
        moduleOutputProvider = config.moduleOutputProvider
      )
    }

    GenerationResults(
      moduleSetResults = moduleSetJobs.awaitAll(),
      dependencyResult = dependencyJob.await(),
      productResult = productJob.await()
    )
  }

  aggregateAndCleanupOrphanedFiles(moduleSetResults)

  printGenerationSummary(
    moduleSetResults = moduleSetResults,
    dependencyResult = dependencyResult,
    productResult = productResult,
    projectRoot = config.projectRoot,
    durationMs = System.currentTimeMillis() - startTime
  )
}
