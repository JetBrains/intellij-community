// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.json

import com.fasterxml.jackson.core.JsonEncoding
import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.productLayout.ModuleSet
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
import org.jetbrains.intellij.build.productLayout.traversal.ModuleSetTraversalCache
import org.jetbrains.intellij.build.productLayout.traversal.checkModuleReachability
import org.jetbrains.intellij.build.productLayout.traversal.findDependencyPath
import org.jetbrains.intellij.build.productLayout.traversal.findModulePaths
import org.jetbrains.intellij.build.productLayout.traversal.getModuleDependencies
import org.jetbrains.intellij.build.productLayout.validation.rules.validateNoRedundantModuleSets
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
 * @param outputProvider Optional provider for accessing JPS modules (required for dependency analysis filters)
 */
suspend fun streamModuleAnalysisJson(
  allModuleSets: List<ModuleSetMetadata>,
  products: List<ProductSpec>,
  projectRoot: Path,
  filter: JsonFilter? = null,
  outputProvider: ModuleOutputProvider? = null,
) {
  // Validate product specifications using shared validation (always validate, even when filtering)
  val moduleSets = allModuleSets.map { it.moduleSet }
  val productSpecs = products.map { it.name to it.contentSpec }
  validateNoRedundantModuleSets(moduleSets, productSpecs)

  // Enrich products with calculated metrics (parallelized)
  val enrichedProducts = enrichProductsWithMetrics(products, moduleSets)

  val generator = JsonFactory()
    .createGenerator(System.out, JsonEncoding.UTF8)
    .configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false)
    .configure(JsonGenerator.Feature.FLUSH_PASSED_TO_STREAM, false)
    .useDefaultPrettyPrinter()

  generator.use { gen ->
    gen.writeStartObject()
    gen.writeStringField("timestamp", Instant.now().toString())

    // Apply filter (dispatches to handler functions)
    applyFilter(gen, filter, allModuleSets, enrichedProducts, moduleSets, projectRoot, outputProvider)

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
  moduleSets: List<ModuleSet>,
  projectRoot: Path,
  outputProvider: ModuleOutputProvider?,
) {
  // Create cache once for all filter handlers that need it
  val cache = ModuleSetTraversalCache(moduleSets)

  when (filter?.filter) {
    null -> writeAllSections(gen, allModuleSets, products, projectRoot)
    "products" -> handleProductsFilter(gen, products)
    "moduleSets" -> handleModuleSetsFilter(gen, allModuleSets, cache)
    "composition" -> handleCompositionFilter(gen, products)
    "duplicates" -> handleDuplicatesFilter(gen, allModuleSets, products, projectRoot, cache)
    "product" -> handleSingleProductFilter(gen, filter.value, products)
    "moduleSet" -> handleSingleModuleSetFilter(gen, filter.value, allModuleSets)
    "mergeImpact" -> handleMergeImpactFilter(gen, filter, allModuleSets, products, cache)
    "modulePaths" -> handleModulePathsFilter(gen, filter.module, allModuleSets, products, projectRoot)
    "moduleDependencies" -> handleModuleDependenciesFilter(gen, filter, outputProvider)
    "moduleReachability" -> handleModuleReachabilityFilter(gen, filter, allModuleSets, outputProvider)
    "dependencyPath" -> handleDependencyPathFilter(gen, filter, outputProvider)
    "productUsage" -> handleProductUsageFilter(gen, filter.moduleSet, allModuleSets, products, cache)
    else -> gen.writeStringField("error", "Unknown filter: ${filter.filter}")
  }
}

private fun handleProductsFilter(gen: JsonGenerator, products: List<ProductSpec>) {
  gen.writeArrayFieldStart("products")
  for (product in products) {
    writeProduct(gen, product)
  }
  gen.writeEndArray()
}

private fun handleModuleSetsFilter(gen: JsonGenerator, allModuleSets: List<ModuleSetMetadata>, cache: ModuleSetTraversalCache) {
  gen.writeArrayFieldStart("moduleSets")
  for ((moduleSet, location, sourceFilePath) in allModuleSets) {
    writeModuleSet(gen, moduleSet, location.name, sourceFilePath, cache)
  }
  gen.writeEndArray()
}

private fun handleCompositionFilter(gen: JsonGenerator, products: List<ProductSpec>) {
  gen.writeObjectFieldStart("productCompositionAnalysis")
  writeProductCompositionAnalysis(gen, products)
  gen.writeEndObject()
}

private fun handleDuplicatesFilter(gen: JsonGenerator, allModuleSets: List<ModuleSetMetadata>, products: List<ProductSpec>, projectRoot: Path, cache: ModuleSetTraversalCache) {
  gen.writeObjectFieldStart("duplicateAnalysis")
  writeDuplicateAnalysis(gen, allModuleSets, products, projectRoot, cache)
  gen.writeEndObject()
}

private fun handleSingleProductFilter(gen: JsonGenerator, productName: String?, products: List<ProductSpec>) {
  if (productName == null) {
    gen.writeStringField("error", "Product name required for 'product' filter")
    return
  }
  val product = products.firstOrNull { it.name == productName }
  if (product != null) {
    gen.writeFieldName("product")
    writeProduct(gen, product)
  }
  else {
    gen.writeStringField("error", "Product '$productName' not found")
  }
}

private fun handleSingleModuleSetFilter(gen: JsonGenerator, moduleSetName: String?, allModuleSets: List<ModuleSetMetadata>) {
  if (moduleSetName == null) {
    gen.writeStringField("error", "Module set name required for 'moduleSet' filter")
    return
  }
  val moduleSetEntry = allModuleSets.firstOrNull { it.moduleSet.name == moduleSetName }
  if (moduleSetEntry != null) {
    gen.writeFieldName("moduleSet")
    gen.writeStartObject()
    gen.writeStringField("name", moduleSetEntry.moduleSet.name)
    gen.writeStringField("location", moduleSetEntry.location.name)
    gen.writeStringField("sourceFile", moduleSetEntry.sourceFile)
    gen.writeArrayFieldStart("directNestedSets")
    for (nestedSet in moduleSetEntry.directNestedSets) {
      gen.writeString(nestedSet)
    }
    gen.writeEndArray()
    val moduleSetJson = kotlinxJson.encodeToString(moduleSetEntry.moduleSet)
    gen.writeFieldName("moduleSet")
    gen.writeRawValue(moduleSetJson)
    gen.writeEndObject()
  }
  else {
    gen.writeStringField("error", "Module set '$moduleSetName' not found")
  }
}

private fun handleMergeImpactFilter(gen: JsonGenerator, filter: JsonFilter, allModuleSets: List<ModuleSetMetadata>, products: List<ProductSpec>, cache: ModuleSetTraversalCache) {
  val sourceSet = filter.source
  if (sourceSet == null) {
    gen.writeStringField("error", "Source module set required for 'mergeImpact' filter")
    return
  }
  val operation = MergeOperation.fromString(filter.operation ?: "merge")
  val impact = analyzeMergeImpact(sourceSet, filter.target, operation, allModuleSets, products, cache)
  gen.writeFieldName("mergeImpact")
  writeMergeImpactAnalysis(gen, impact)
}

private fun handleModulePathsFilter(gen: JsonGenerator, moduleName: String?, allModuleSets: List<ModuleSetMetadata>, products: List<ProductSpec>, projectRoot: Path) {
  if (moduleName == null) {
    gen.writeStringField("error", "Module name required for 'modulePaths' filter")
    return
  }
  val pathsResult = findModulePaths(moduleName, allModuleSets, products, projectRoot)
  gen.writeFieldName("modulePaths")
  writeModulePathsResult(gen, pathsResult)
}

private fun handleModuleDependenciesFilter(gen: JsonGenerator, filter: JsonFilter, outputProvider: ModuleOutputProvider?) {
  val moduleName = filter.module
  if (moduleName == null) {
    gen.writeStringField("error", "Module name required for 'moduleDependencies' filter")
    return
  }
  if (outputProvider == null) {
    gen.writeStringField("error", "ModuleOutputProvider required for moduleDependencies filter")
    return
  }
  val result = getModuleDependencies(moduleName, outputProvider, filter.includeTransitive)
  gen.writeFieldName("moduleDependencies")
  writeModuleDependenciesResult(gen, result)
}

private fun handleModuleReachabilityFilter(gen: JsonGenerator, filter: JsonFilter, allModuleSets: List<ModuleSetMetadata>, outputProvider: ModuleOutputProvider?) {
  val moduleName = filter.module
  val moduleSetName = filter.moduleSet
  if (moduleName == null || moduleSetName == null) {
    gen.writeStringField("error", "Module name and module set name required for 'moduleReachability' filter")
    return
  }
  if (outputProvider == null) {
    gen.writeStringField("error", "ModuleOutputProvider required for moduleReachability filter")
    return
  }
  val result = checkModuleReachability(moduleName, moduleSetName, allModuleSets, outputProvider)
  gen.writeFieldName("moduleReachability")
  writeModuleReachabilityResult(gen, result)
}

private fun handleDependencyPathFilter(gen: JsonGenerator, filter: JsonFilter, outputProvider: ModuleOutputProvider?) {
  val fromModule = filter.fromModule
  val toModule = filter.toModule
  if (fromModule == null || toModule == null) {
    gen.writeStringField("error", "Both fromModule and toModule required for 'dependencyPath' filter")
    return
  }
  if (outputProvider == null) {
    gen.writeStringField("error", "ModuleOutputProvider required for dependencyPath filter")
    return
  }
  val result = findDependencyPath(fromModule, toModule, outputProvider)
  gen.writeFieldName("dependencyPath")
  writeDependencyPathResult(gen, result)
}

private fun handleProductUsageFilter(
  gen: JsonGenerator,
  moduleSetName: String?,
  allModuleSets: List<ModuleSetMetadata>,
  products: List<ProductSpec>,
  cache: ModuleSetTraversalCache,
) {
  if (moduleSetName == null) {
    gen.writeStringField("error", "Module set name required for 'productUsage' filter")
    return
  }
  val usage = analyzeProductUsage(moduleSetName, products, allModuleSets, cache)
  gen.writeFieldName("productUsage")
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
) {
  // Write module sets
  val moduleSets = allModuleSets.map { it.moduleSet }
  // Create cache for O(1) lookups during analysis (used by multiple sections below)
  val cache = ModuleSetTraversalCache(moduleSets)

  gen.writeArrayFieldStart("moduleSets")
  for ((moduleSet, location, sourceFilePath) in allModuleSets) {
    writeModuleSet(gen = gen, moduleSet = moduleSet, location = location.name, sourceFilePath = sourceFilePath, cache = cache)
  }
  gen.writeEndArray()

  // Write products
  gen.writeArrayFieldStart("products")
  for (product in products) {
    writeProduct(gen, product)
  }
  gen.writeEndArray()

  // Write duplicate analysis
  gen.writeObjectFieldStart("duplicateAnalysis")
  writeDuplicateAnalysis(gen = gen, allModuleSets = allModuleSets, products = products, projectRoot = projectRoot, cache = cache)
  gen.writeEndObject()

  // Write product composition analysis
  gen.writeObjectFieldStart("productCompositionAnalysis")
  writeProductCompositionAnalysis(gen, products)
  gen.writeEndObject()

  // Write module distribution analysis
  val moduleLocationsResult = parseModulesXml(projectRoot)
  val moduleLocations = when (moduleLocationsResult) {
    is ParseResult.Success -> moduleLocationsResult.value
    is ParseResult.Failure -> {
      gen.writeStringField("moduleLocationsWarning", moduleLocationsResult.error)
      moduleLocationsResult.partial ?: emptyMap()
    }
  }
  gen.writeFieldName("moduleDistribution")
  writeModuleDistribution(gen = gen, allModuleSets = allModuleSets, products = products, moduleLocations = moduleLocations, cache = cache)

  // Write module set hierarchy
  gen.writeFieldName("moduleSetHierarchy")
  writeModuleSetHierarchy(gen, allModuleSets)

  // Write module usage index
  gen.writeFieldName("moduleUsageIndex")
  writeModuleUsageIndex(gen, allModuleSets, products, cache)

  // Validate community products don't use ultimate modules
  val communityViolations = validateCommunityProducts(
    products = products,
    allModuleSets = allModuleSets,
    moduleLocations = moduleLocations,
    projectRoot = projectRoot,
    cache = cache,
  )
  gen.writeObjectFieldStart("communityProductViolations")
  writeCommunityProductViolations(gen, communityViolations)
  gen.writeEndObject()

  // Validate module sets are in correct locations
  val locationViolations = validateModuleSetLocations(allModuleSets, moduleLocations, projectRoot)
  gen.writeObjectFieldStart("moduleSetLocationViolations")
  writeModuleSetLocationViolations(gen, locationViolations)
  gen.writeEndObject()

  // Analyze product similarity for refactoring recommendations (parallelized)
  val similarityPairs = analyzeProductSimilarity(products, similarityThreshold = 0.7)
  gen.writeFieldName("productSimilarity")
  writeProductSimilarityAnalysis(gen, similarityPairs, 0.7)

  // Detect module set overlaps with cache (parallelized)
  val moduleSetOverlaps = detectModuleSetOverlap(allModuleSets, cache, minOverlapPercent = 50)
  gen.writeFieldName("moduleSetOverlap")
  writeModuleSetOverlapAnalysis(gen, moduleSetOverlaps, 50)

  // Generate unification suggestions based on overlaps and similarity (parallelized)
  val unificationSuggestions = suggestModuleSetUnification(
    allModuleSets = allModuleSets,
    products = products,
    overlaps = moduleSetOverlaps,
    similarityPairs = similarityPairs,
    maxSuggestions = 10,
    strategy = "all"
  )
  gen.writeFieldName("unificationSuggestions")
  writeUnificationSuggestions(gen, unificationSuggestions)
}
