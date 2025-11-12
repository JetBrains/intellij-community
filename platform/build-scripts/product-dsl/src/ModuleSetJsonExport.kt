// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout

import com.fasterxml.jackson.core.JsonEncoding
import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import org.jetbrains.intellij.build.productLayout.analysis.JsonFilter
import org.jetbrains.intellij.build.productLayout.analysis.ModuleSetMetadata
import org.jetbrains.intellij.build.productLayout.analysis.ProductSpec
import org.jetbrains.intellij.build.productLayout.analysis.analyzeMergeImpact
import org.jetbrains.intellij.build.productLayout.analysis.analyzeProductSimilarity
import org.jetbrains.intellij.build.productLayout.analysis.detectModuleSetOverlap
import org.jetbrains.intellij.build.productLayout.analysis.findModulePaths
import org.jetbrains.intellij.build.productLayout.analysis.parseModulesXml
import org.jetbrains.intellij.build.productLayout.analysis.suggestModuleSetUnification
import org.jetbrains.intellij.build.productLayout.analysis.validateCommunityProducts
import org.jetbrains.intellij.build.productLayout.analysis.validateModuleSetLocations
import org.jetbrains.intellij.build.productLayout.json.enrichProductsWithMetrics
import org.jetbrains.intellij.build.productLayout.json.writeCommunityProductViolations
import org.jetbrains.intellij.build.productLayout.json.writeDuplicateAnalysis
import org.jetbrains.intellij.build.productLayout.json.writeMergeImpactAnalysis
import org.jetbrains.intellij.build.productLayout.json.writeModuleDistribution
import org.jetbrains.intellij.build.productLayout.json.writeModulePathsResult
import org.jetbrains.intellij.build.productLayout.json.writeModuleSet
import org.jetbrains.intellij.build.productLayout.json.writeModuleSetHierarchy
import org.jetbrains.intellij.build.productLayout.json.writeModuleSetLocationViolations
import org.jetbrains.intellij.build.productLayout.json.writeModuleSetOverlapAnalysis
import org.jetbrains.intellij.build.productLayout.json.writeModuleUsageIndex
import org.jetbrains.intellij.build.productLayout.json.writeProduct
import org.jetbrains.intellij.build.productLayout.json.writeProductCompositionAnalysis
import org.jetbrains.intellij.build.productLayout.json.writeProductSimilarityAnalysis
import org.jetbrains.intellij.build.productLayout.json.writeUnificationSuggestions
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
fun streamModuleAnalysisJson(
  allModuleSets: List<ModuleSetMetadata>,
  products: List<ProductSpec>,
  projectRoot: Path,
  filter: JsonFilter? = null
) {
  // Validate product specifications using shared validation (always validate, even when filtering)
  val moduleSets = allModuleSets.map { it.moduleSet }
  val productSpecs = products.map { it.name to it.contentSpec }
  validateNoRedundantModuleSets(moduleSets, productSpecs)

  // Enrich products with calculated metrics
  val enrichedProducts = enrichProductsWithMetrics(products, moduleSets)

  val generator = JsonFactory()
    .createGenerator(System.out, JsonEncoding.UTF8)
    .configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false)
    .configure(JsonGenerator.Feature.FLUSH_PASSED_TO_STREAM, false)
    .useDefaultPrettyPrinter()

  generator.use { gen ->
    gen.writeStartObject()
    gen.writeStringField("timestamp", Instant.now().toString())

    // Apply filter
    when {
      filter == null -> {
        // Full JSON (no filter)
        writeAllSections(gen, allModuleSets, enrichedProducts, projectRoot)
      }
      filter.filter == "products" -> {
        gen.writeArrayFieldStart("products")
        for (product in enrichedProducts) {
          writeProduct(gen, product)
        }
        gen.writeEndArray()
      }
      filter.filter == "moduleSets" -> {
        gen.writeArrayFieldStart("moduleSets")
        for ((moduleSet, location, sourceFilePath) in allModuleSets) {
          writeModuleSet(gen, moduleSet, location, sourceFilePath, moduleSets)
        }
        gen.writeEndArray()
      }
      filter.filter == "composition" -> {
        gen.writeObjectFieldStart("productCompositionAnalysis")
        writeProductCompositionAnalysis(gen, enrichedProducts)
        gen.writeEndObject()
      }
      filter.filter == "duplicates" -> {
        gen.writeObjectFieldStart("duplicateAnalysis")
        writeDuplicateAnalysis(gen, allModuleSets, enrichedProducts, projectRoot)
        gen.writeEndObject()
      }
      filter.filter == "product" && filter.value != null -> {
        val productName = filter.value
        val product = enrichedProducts.firstOrNull { it.name == productName }
        if (product != null) {
          gen.writeFieldName("product")
          gen.writeStartObject()
          // Copy writeProduct logic inline but without outer object wrapper
          gen.writeStringField("name", product.name)
          gen.writeStringField("className", product.className)
          gen.writeStringField("sourceFile", product.sourceFile)
          if (product.pluginXmlPath != null) {
            gen.writeStringField("pluginXmlPath", product.pluginXmlPath)
          }
          if (product.contentSpec != null) {
            val contentSpecJson = org.jetbrains.intellij.build.productLayout.json.kotlinxJson.encodeToString(product.contentSpec)
            gen.writeFieldName("contentSpec")
            gen.writeRawValue(contentSpecJson)
          }
          gen.writeArrayFieldStart("buildModules")
          for (buildModule in product.buildModules) {
            gen.writeString(buildModule)
          }
          gen.writeEndArray()
          gen.writeEndObject()
        } else {
          gen.writeStringField("error", "Product '$productName' not found")
        }
      }
      filter.filter == "moduleSet" && filter.value != null -> {
        val moduleSetName = filter.value
        val moduleSetEntry = allModuleSets.firstOrNull { it.moduleSet.name == moduleSetName }
        if (moduleSetEntry != null) {
          gen.writeFieldName("moduleSet")
          gen.writeStartObject()
          gen.writeStringField("name", moduleSetEntry.moduleSet.name)
          gen.writeStringField("location", moduleSetEntry.location)
          gen.writeStringField("sourceFile", moduleSetEntry.sourceFile)
          val moduleSetJson = org.jetbrains.intellij.build.productLayout.json.kotlinxJson.encodeToString(moduleSetEntry.moduleSet)
          gen.writeFieldName("moduleSet")
          gen.writeRawValue(moduleSetJson)
          gen.writeEndObject()
        } else {
          gen.writeStringField("error", "Module set '$moduleSetName' not found")
        }
      }
      filter.filter == "mergeImpact" && filter.source != null -> {
        val sourceSet = filter.source
        val targetSet = filter.target
        val operation = filter.operation ?: "merge"

        val impact = analyzeMergeImpact(sourceSet, targetSet, operation, allModuleSets, enrichedProducts)
        gen.writeFieldName("mergeImpact")
        gen.writeStartObject()
        writeMergeImpactAnalysis(gen, impact)
        gen.writeEndObject()
      }
      filter.filter == "modulePaths" && filter.module != null -> {
        val moduleName = filter.module

        val pathsResult = findModulePaths(moduleName, allModuleSets, enrichedProducts, projectRoot)
        gen.writeFieldName("modulePaths")
        gen.writeStartObject()
        writeModulePathsResult(gen, pathsResult)
        gen.writeEndObject()
      }
      else -> {
        gen.writeStringField("error", "Unknown filter: ${filter.filter}")
      }
    }

    gen.writeEndObject()
  }

  // Flush stdout to ensure all data is written
  System.out.flush()
}

/**
 * Writes all sections of the analysis JSON (used when no filter is specified).
 */
private fun writeAllSections(
  gen: JsonGenerator,
  allModuleSets: List<ModuleSetMetadata>,
  products: List<ProductSpec>,
  projectRoot: Path
) {
  // Write module sets
  val moduleSets = allModuleSets.map { it.moduleSet }
  gen.writeArrayFieldStart("moduleSets")
  for ((moduleSet, location, sourceFilePath) in allModuleSets) {
    writeModuleSet(gen, moduleSet, location, sourceFilePath, moduleSets)
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
  writeDuplicateAnalysis(gen, allModuleSets, products, projectRoot)
  gen.writeEndObject()

  // Write product composition analysis
  gen.writeObjectFieldStart("productCompositionAnalysis")
  writeProductCompositionAnalysis(gen, products)
  gen.writeEndObject()

  // Write module distribution analysis
  val moduleLocations = parseModulesXml(projectRoot)
  gen.writeObjectFieldStart("moduleDistribution")
  writeModuleDistribution(gen, allModuleSets, products, moduleLocations)
  gen.writeEndObject()

  // Write module set hierarchy
  gen.writeObjectFieldStart("moduleSetHierarchy")
  writeModuleSetHierarchy(gen, allModuleSets)
  gen.writeEndObject()

  // Write module usage index
  gen.writeObjectFieldStart("moduleUsageIndex")
  writeModuleUsageIndex(gen, allModuleSets, products)
  gen.writeEndObject()

  // Validate community products don't use ultimate modules
  val communityViolations = validateCommunityProducts(products, allModuleSets, moduleLocations, projectRoot)
  gen.writeObjectFieldStart("communityProductViolations")
  writeCommunityProductViolations(gen, communityViolations)
  gen.writeEndObject()

  // Validate module sets are in correct locations
  val locationViolations = validateModuleSetLocations(allModuleSets, moduleLocations, projectRoot)
  gen.writeObjectFieldStart("moduleSetLocationViolations")
  writeModuleSetLocationViolations(gen, locationViolations)
  gen.writeEndObject()

  // Analyze product similarity for refactoring recommendations
  val similarityPairs = analyzeProductSimilarity(products, similarityThreshold = 0.7)
  gen.writeObjectFieldStart("productSimilarity")
  writeProductSimilarityAnalysis(gen, similarityPairs, 0.7)
  gen.writeEndObject()

  // Detect module set overlaps (with nested set filtering to avoid false positives)
  val moduleSetOverlaps = detectModuleSetOverlap(allModuleSets, minOverlapPercent = 50)
  gen.writeObjectFieldStart("moduleSetOverlap")
  writeModuleSetOverlapAnalysis(gen, moduleSetOverlaps, 50)
  gen.writeEndObject()

  // Generate unification suggestions based on overlaps and similarity
  val unificationSuggestions = suggestModuleSetUnification(
    allModuleSets = allModuleSets,
    products = products,
    overlaps = moduleSetOverlaps,
    similarityPairs = similarityPairs,
    maxSuggestions = 10,
    strategy = "all"
  )
  gen.writeObjectFieldStart("unificationSuggestions")
  writeUnificationSuggestions(gen, unificationSuggestions)
  gen.writeEndObject()
}
