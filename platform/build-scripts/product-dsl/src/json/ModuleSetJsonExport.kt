// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.json

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.PluginGraph
import com.intellij.platform.pluginGraph.TargetName
import org.jetbrains.intellij.build.productLayout.tooling.JsonFilter
import org.jetbrains.intellij.build.productLayout.tooling.MergeOperation
import org.jetbrains.intellij.build.productLayout.tooling.ModuleSetMetadata
import org.jetbrains.intellij.build.productLayout.tooling.ParseResult
import org.jetbrains.intellij.build.productLayout.tooling.ProductSpec
import org.jetbrains.intellij.build.productLayout.tooling.analyzeMergeImpact
import org.jetbrains.intellij.build.productLayout.tooling.analyzeProductSimilarity
import org.jetbrains.intellij.build.productLayout.tooling.analyzeProductUsage
import org.jetbrains.intellij.build.productLayout.tooling.detectModuleSetOverlap
import org.jetbrains.intellij.build.productLayout.tooling.suggestModuleSetUnification
import org.jetbrains.intellij.build.productLayout.traversal.checkModuleReachability
import org.jetbrains.intellij.build.productLayout.traversal.findDependencyPath
import org.jetbrains.intellij.build.productLayout.traversal.findModulePaths
import org.jetbrains.intellij.build.productLayout.traversal.getModuleDependencies
import org.jetbrains.intellij.build.productLayout.traversal.getModuleOwners
import org.jetbrains.intellij.build.productLayout.validator.rule.validateNoRedundantModuleSets
import tools.jackson.core.JsonEncoding
import tools.jackson.core.JsonGenerator
import tools.jackson.core.ObjectWriteContext
import tools.jackson.core.PrettyPrinter
import tools.jackson.core.StreamWriteFeature
import tools.jackson.core.json.JsonFactory
import tools.jackson.core.util.DefaultPrettyPrinter
import java.nio.file.Path
import java.time.Instant

/**
 * Streams comprehensive module set and product analysis data as JSON to stdout.
 * Uses hybrid approach:
 * - Jackson JsonGenerator for overall structure and analysis sections
 * - kotlinx.serialization for automatic serialization of DSL data structures (ModuleSet, ProductModulesContentSpec)
 *
 * Generic function that accepts discovered module sets and products - similar to buildProductContentXml pattern.
 * Caller is responsible for discovering module sets and products and providing them.
 *
 * Output includes:
 * - Module sets with complete ModuleSet structure (modules, nested sets, aliases)
 * - Products with full ProductModulesContentSpec (module sets with overrides, additional modules, exclusions)
 * - Source file paths (relative to project root) for easy navigation
 * - Duplicate analysis (modules in multiple sets)
 * - Set overlap analysis (for unification opportunities)
 *
 * This data enables AI to:
 * - See complete product DSL specifications with all modules and overrides
 * - Detect duplicate modules across module sets
 * - Find unification opportunities
 * - Identify redundant includes or module specifications
 * - Analyze module set overlap and relationships
 * - Navigate to source files to make changes
 *
 * @param allModuleSets List of ModuleSetMetadata instances with module set, location, and source file path
 * @param products List of ProductSpec instances
 * @param projectRoot Project root path for resolving .idea/modules.xml
 * @param filter Optional filter: null (full), or JsonFilter object with filter type and optional value
 */
suspend fun streamModuleAnalysisJson(
  allModuleSets: List<ModuleSetMetadata>,
  products: List<ProductSpec>,
  projectRoot: Path,
  filter: JsonFilter? = null,
  pluginGraph: PluginGraph,
) {
  // Validate product specifications using shared validation (always validate, even when filtering)
  val moduleSets = allModuleSets.map { it.moduleSet }
  val productSpecs = products.map { it.name to it.contentSpec }
  validateNoRedundantModuleSets(moduleSets, productSpecs)

  // Enrich products with calculated metrics (parallelized)
  val enrichedProducts = enrichProductsWithMetrics(products, pluginGraph)

  val jsonFactory = JsonFactory()
  val writeContext = object : ObjectWriteContext.Base() {
    override fun tokenStreamFactory() = jsonFactory
    override fun getPrettyPrinter(): PrettyPrinter = DefaultPrettyPrinter()
  }
  val generator = jsonFactory
    .createGenerator(writeContext, System.out, JsonEncoding.UTF8)
    .configure(StreamWriteFeature.AUTO_CLOSE_TARGET, false)
    .configure(StreamWriteFeature.FLUSH_PASSED_TO_STREAM, false)

  generator.use { gen ->
    gen.writeStartObject()
    gen.writeStringProperty("timestamp", Instant.now().toString())

    // Apply filter (dispatches to handler functions)
    applyFilter(gen, filter, allModuleSets, enrichedProducts, projectRoot, pluginGraph)

    gen.writeEndObject()
  }

  // Flush stdout to ensure all data is written
  System.out.flush()
}

/**
 * Dispatches filter handling to appropriate handler functions.
 */
private suspend fun applyFilter(
  gen: JsonGenerator,
  filter: JsonFilter?,
  allModuleSets: List<ModuleSetMetadata>,
  products: List<ProductSpec>,
  projectRoot: Path,
  pluginGraph: PluginGraph,
) {
  when (filter?.filter) {
    null -> writeAllSections(gen, allModuleSets, products, projectRoot, pluginGraph)
    "products" -> handleProductsFilter(gen, products)
    "moduleSets" -> handleModuleSetsFilter(gen, allModuleSets, pluginGraph)
    "composition" -> handleCompositionFilter(gen, products)
    "duplicates" -> handleDuplicatesFilter(gen, allModuleSets, pluginGraph)
    "product" -> handleSingleProductFilter(gen, filter.value, products)
    "moduleSet" -> handleSingleModuleSetFilter(gen, filter.value, allModuleSets, pluginGraph)
    "mergeImpact" -> handleMergeImpactFilter(gen, filter, allModuleSets, products, pluginGraph)
    "modulePaths" -> handleModulePathsFilter(gen, filter.module, allModuleSets, products, projectRoot)
    "moduleDependencies" -> handleModuleDependenciesFilter(gen, filter, pluginGraph)
    "moduleOwners" -> handleModuleOwnersFilter(gen, filter, pluginGraph)
    "moduleReachability" -> handleModuleReachabilityFilter(gen, filter, pluginGraph)
    "dependencyPath" -> handleDependencyPathFilter(gen, filter, pluginGraph)
    "productUsage" -> handleProductUsageFilter(gen, filter.moduleSet, products, pluginGraph)
    else -> gen.writeStringProperty("error", "Unknown filter: ${filter.filter}")
  }
}

private fun handleProductsFilter(gen: JsonGenerator, products: List<ProductSpec>) {
  gen.writeArrayPropertyStart("products")
  for (product in products) {
    writeProduct(gen, product)
  }
  gen.writeEndArray()
}

private fun handleModuleSetsFilter(
  gen: JsonGenerator,
  allModuleSets: List<ModuleSetMetadata>,
  pluginGraph: PluginGraph,
) {
  gen.writeArrayPropertyStart("moduleSets")
  for ((moduleSet, location, sourceFilePath) in allModuleSets) {
    writeModuleSet(gen, moduleSet, location.name, sourceFilePath, pluginGraph)
  }
  gen.writeEndArray()
}

private fun handleCompositionFilter(gen: JsonGenerator, products: List<ProductSpec>) {
  gen.writeObjectPropertyStart("productCompositionAnalysis")
  writeProductCompositionAnalysis(gen, products)
  gen.writeEndObject()
}

private fun handleDuplicatesFilter(
  gen: JsonGenerator,
  allModuleSets: List<ModuleSetMetadata>,
  pluginGraph: PluginGraph,
) {
  gen.writeObjectPropertyStart("duplicateAnalysis")
  writeDuplicateAnalysis(gen, allModuleSets, pluginGraph)
  gen.writeEndObject()
}

private fun handleSingleProductFilter(gen: JsonGenerator, productName: String?, products: List<ProductSpec>) {
  if (productName == null) {
    gen.writeStringProperty("error", "Product name required for 'product' filter")
    return
  }
  val product = products.firstOrNull { it.name == productName }
  if (product != null) {
    gen.writeName("product")
    writeProduct(gen, product)
  }
  else {
    gen.writeStringProperty("error", "Product '$productName' not found")
  }
}

private fun handleSingleModuleSetFilter(
  gen: JsonGenerator,
  moduleSetName: String?,
  allModuleSets: List<ModuleSetMetadata>,
  pluginGraph: PluginGraph,
) {
  if (moduleSetName == null) {
    gen.writeStringProperty("error", "Module set name required for 'moduleSet' filter")
    return
  }
  val moduleSetEntry = allModuleSets.firstOrNull { it.moduleSet.name == moduleSetName }
  if (moduleSetEntry != null) {
    gen.writeName("moduleSet")
    writeModuleSet(
      gen = gen,
      moduleSet = moduleSetEntry.moduleSet,
      location = moduleSetEntry.location.name,
      sourceFilePath = moduleSetEntry.sourceFile,
      pluginGraph = pluginGraph
    )
  }
  else {
    gen.writeStringProperty("error", "Module set '$moduleSetName' not found")
  }
}

private fun handleMergeImpactFilter(
  gen: JsonGenerator,
  filter: JsonFilter,
  allModuleSets: List<ModuleSetMetadata>,
  products: List<ProductSpec>,
  pluginGraph: PluginGraph,
) {
  val sourceSet = filter.source
  if (sourceSet == null) {
    gen.writeStringProperty("error", "Source module set required for 'mergeImpact' filter")
    return
  }
  val operation = MergeOperation.fromString(filter.operation ?: "merge")
  val impact = analyzeMergeImpact(sourceSet, filter.target, operation, allModuleSets, products, pluginGraph)
  gen.writeName("mergeImpact")
  writeMergeImpactAnalysis(gen, impact)
}

private fun handleModulePathsFilter(gen: JsonGenerator, moduleName: String?, allModuleSets: List<ModuleSetMetadata>, products: List<ProductSpec>, projectRoot: Path) {
  if (moduleName == null) {
    gen.writeStringProperty("error", "Module name required for 'modulePaths' filter")
    return
  }
  val pathsResult = findModulePaths(ContentModuleName(moduleName), allModuleSets, products, projectRoot)
  gen.writeName("modulePaths")
  writeModulePathsResult(gen, pathsResult)
}

private fun handleModuleDependenciesFilter(
  gen: JsonGenerator,
  filter: JsonFilter,
  pluginGraph: PluginGraph,
) {
  val moduleName = filter.module
  if (moduleName == null) {
    gen.writeStringProperty("error", "Module name required for 'moduleDependencies' filter")
    return
  }
  val result = getModuleDependencies(
    moduleName = TargetName(moduleName),
    graph = pluginGraph,
    includeTransitive = filter.includeTransitive,
    includeTestDependencies = filter.includeTestDependencies,
  )
  gen.writeName("moduleDependencies")
  writeModuleDependenciesResult(gen, result)
}

private fun handleModuleOwnersFilter(
  gen: JsonGenerator,
  filter: JsonFilter,
  pluginGraph: PluginGraph,
) {
  val moduleName = filter.module
  if (moduleName == null) {
    gen.writeStringProperty("error", "Module name required for 'moduleOwners' filter")
    return
  }
  val result = getModuleOwners(ContentModuleName(moduleName), pluginGraph, filter.includeTestSources)
  gen.writeName("moduleOwners")
  writeModuleOwnersResult(gen, result)
}

private fun handleModuleReachabilityFilter(
  gen: JsonGenerator,
  filter: JsonFilter,
  pluginGraph: PluginGraph,
) {
  val moduleName = filter.module
  val moduleSetName = filter.moduleSet
  if (moduleName == null || moduleSetName == null) {
    gen.writeStringProperty("error", "Module name and module set name required for 'moduleReachability' filter")
    return
  }
  val result = checkModuleReachability(ContentModuleName(moduleName), moduleSetName, pluginGraph)
  gen.writeName("moduleReachability")
  writeModuleReachabilityResult(gen, result)
}

private fun handleDependencyPathFilter(
  gen: JsonGenerator,
  filter: JsonFilter,
  pluginGraph: PluginGraph,
) {
  val fromModule = filter.fromModule
  val toModule = filter.toModule
  if (fromModule == null || toModule == null) {
    gen.writeStringProperty("error", "Both fromModule and toModule required for 'dependencyPath' filter")
    return
  }
  val graphType = filter.graph?.lowercase()
  if (graphType != null && graphType != "jps") {
    gen.writeStringProperty("error", "Unsupported dependencyPath graph '$graphType'. Supported: jps")
    return
  }
  val result = findDependencyPath(
    fromModule = TargetName(fromModule),
    toModule = TargetName(toModule),
    graph = pluginGraph,
    includeTestDependencies = filter.includeTestDependencies,
    includeScopes = filter.includeScopes,
  )
  gen.writeName("dependencyPath")
  writeDependencyPathResult(gen, result)
}

private fun handleProductUsageFilter(
  gen: JsonGenerator,
  moduleSetName: String?,
  products: List<ProductSpec>,
  pluginGraph: PluginGraph,
) {
  if (moduleSetName == null) {
    gen.writeStringProperty("error", "Module set name required for 'productUsage' filter")
    return
  }
  val usage = analyzeProductUsage(moduleSetName, products, pluginGraph)
  gen.writeName("productUsage")
  writeProductUsageAnalysis(gen, usage)
}

/**
 * Writes all sections of the analysis JSON (used when no filter is specified).
 */
private suspend fun writeAllSections(
  gen: JsonGenerator,
  allModuleSets: List<ModuleSetMetadata>,
  products: List<ProductSpec>,
  projectRoot: Path,
  pluginGraph: PluginGraph,
) {
  // Write module sets
  gen.writeArrayPropertyStart("moduleSets")
  for ((moduleSet, location, sourceFilePath) in allModuleSets) {
    writeModuleSet(gen = gen, moduleSet = moduleSet, location = location.name, sourceFilePath = sourceFilePath, pluginGraph = pluginGraph)
  }
  gen.writeEndArray()

  // Write module sets index for direct lookup
  gen.writeName("moduleSetsByName")
  writeModuleSetIndex(gen, allModuleSets, pluginGraph)

  // Write products
  gen.writeArrayPropertyStart("products")
  for (product in products) {
    writeProduct(gen, product)
  }
  gen.writeEndArray()

  // Write products index for direct lookup
  gen.writeName("productsByName")
  writeProductIndex(gen, products)

  // Write duplicate analysis
  gen.writeObjectPropertyStart("duplicateAnalysis")
  writeDuplicateAnalysis(gen = gen, allModuleSets = allModuleSets, pluginGraph = pluginGraph)
  gen.writeEndObject()

  // Write product composition analysis
  gen.writeObjectPropertyStart("productCompositionAnalysis")
  writeProductCompositionAnalysis(gen, products)
  gen.writeEndObject()

  // Write module distribution analysis
  val moduleLocationsResult = parseModulesXml(projectRoot)
  val moduleLocations = when (moduleLocationsResult) {
    is ParseResult.Success -> moduleLocationsResult.value
    is ParseResult.Failure -> {
      gen.writeStringProperty("moduleLocationsWarning", moduleLocationsResult.error)
      moduleLocationsResult.partial ?: emptyMap()
    }
  }
  gen.writeName("moduleDistribution")
  writeModuleDistribution(gen = gen, allModuleSets = allModuleSets, products = products, moduleLocations = moduleLocations, pluginGraph = pluginGraph)

  // Write module set hierarchy
  gen.writeName("moduleSetHierarchy")
  writeModuleSetHierarchy(gen, allModuleSets, pluginGraph)

  // Write module usage index
  gen.writeName("moduleUsageIndex")
  writeModuleUsageIndex(gen, allModuleSets, products, pluginGraph)

  // Validate community products don't use ultimate modules
  val communityViolations = validateCommunityProducts(
    products = products,
    allModuleSets = allModuleSets,
    moduleLocations = moduleLocations,
    projectRoot = projectRoot,
    pluginGraph = pluginGraph,
  )
  gen.writeObjectPropertyStart("communityProductViolations")
  writeCommunityProductViolations(gen, communityViolations)
  gen.writeEndObject()

  // Validate module sets are in correct locations
  val locationViolations = validateModuleSetLocations(allModuleSets, moduleLocations, projectRoot, pluginGraph)
  gen.writeObjectPropertyStart("moduleSetLocationViolations")
  writeModuleSetLocationViolations(gen, locationViolations)
  gen.writeEndObject()

  // Analyze product similarity for refactoring recommendations (parallelized)
  val similarityPairs = analyzeProductSimilarity(products, pluginGraph, similarityThreshold = 0.7)
  gen.writeName("productSimilarity")
  writeProductSimilarityAnalysis(gen, similarityPairs, 0.7)

  // Detect module set overlaps with cache (parallelized)
  val moduleSetOverlaps = detectModuleSetOverlap(allModuleSets, pluginGraph, minOverlapPercent = 50)
  gen.writeName("moduleSetOverlap")
  writeModuleSetOverlapAnalysis(gen, moduleSetOverlaps, 50)

  // Generate unification suggestions based on overlaps and similarity (parallelized)
  val unificationSuggestions = suggestModuleSetUnification(
    allModuleSets = allModuleSets,
    products = products,
    overlaps = moduleSetOverlaps,
    similarityPairs = similarityPairs,
    pluginGraph = pluginGraph,
    maxSuggestions = 10,
    strategy = "all"
  )
  gen.writeName("unificationSuggestions")
  writeUnificationSuggestions(gen, unificationSuggestions)
}
