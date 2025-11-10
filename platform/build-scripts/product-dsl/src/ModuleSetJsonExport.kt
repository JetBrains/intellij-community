// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout

import com.fasterxml.jackson.core.JsonEncoding
import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import com.intellij.openapi.util.JDOMUtil
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/**
 * Metadata about a module set including its location and source file.
 *
 * @param moduleSet The module set instance
 * @param location The location category ("community" or "ultimate")
 * @param sourceFile The Kotlin source file path relative to project root where this module set is defined
 */
data class ModuleSetMetadata(
  val moduleSet: ModuleSet,
  val location: String,
  val sourceFile: String
)

/**
 * JSON filter for selective analysis output.
 * Supports various filter types: products, moduleSets, composition, duplicates, or specific items.
 */
@Serializable
data class JsonFilter(
  val filter: String,  // "products", "moduleSets", "composition", "duplicates", "product", "moduleSet"
  val value: String? = null,  // Product/module set name when filter is "product" or "moduleSet"
  val includeDuplicates: Boolean = false  // Include duplicate xi:include detection in output (for future unification)
)

/**
 * Product specification for JSON output.
 * Contains essential product metadata with source file paths for AI navigation,
 * plus complete ProductModulesContentSpec for full DSL representation.
 */
data class ProductSpec(
  val name: String,
  val className: String?,
  val sourceFile: String,
  val pluginXmlPath: String?,
  val contentSpec: ProductModulesContentSpec?,
  val buildModules: List<String>,
  val totalModuleCount: Int = 0,      // All modules including from module sets
  val directModuleCount: Int = 0,     // Just additionalModules count
  val moduleSetCount: Int = 0,        // Number of module sets included
  val uniqueModuleCount: Int = 0      // Deduplicated module count
)

/**
 * Module location information from .idea/modules.xml.
 * 
 * @param location Module location: "community", "ultimate", or "unknown"
 * @param imlPath Absolute path to the .iml file
 */
data class ModuleLocationInfo(
  val location: String,  // "community", "ultimate", or "unknown"
  val imlPath: String?
)

/**
 * Parses .idea/modules.xml to determine module locations (community vs ultimate).
 * This information is used by validation functions to ensure architectural constraints.
 * 
 * @param projectRoot Absolute path to the project root directory
 * @return Map of module name to ModuleLocationInfo
 */
fun parseModulesXml(projectRoot: Path): Map<String, ModuleLocationInfo> {
  val modulesXmlPath = projectRoot.resolve(".idea/modules.xml")
  
  if (!Files.exists(modulesXmlPath)) {
    return emptyMap()
  }
  
  val modules = mutableMapOf<String, ModuleLocationInfo>()
  
  try {
    val document = JDOMUtil.load(modulesXmlPath)
    
    // Get all <module> elements under <component name="ProjectModuleManager">
    val projectModuleManager = document.getChildren("component")
      .find { it.getAttributeValue("name") == "ProjectModuleManager" }
    
    val modulesParent = projectModuleManager?.getChild("modules") ?: return emptyMap()
    
    for (moduleElement in modulesParent.getChildren("module")) {
      var filepath = moduleElement.getAttributeValue("filepath") ?: continue
      
      // Replace $PROJECT_DIR$ with actual project root
      filepath = filepath.replace("\$PROJECT_DIR\$", projectRoot.toString())
      
      // Extract module name from .iml filename
      val moduleName = Path.of(filepath).fileName.toString().removeSuffix(".iml")
      
      // Determine location based on filepath
      val location = when {
        filepath.contains("/community/") -> "community"
        filepath.contains("/ultimate/") -> "ultimate"
        else -> "unknown"
      }
      
      modules[moduleName] = ModuleLocationInfo(location, filepath)
    }
  }
  catch (e: Exception) {
    // If parsing fails, return empty map (validation will report as unknown)
    System.err.println("Warning: Failed to parse .idea/modules.xml: ${e.message}")
  }
  
  return modules
}

/**
 * Derives source file path from ProductProperties class name.
 * Maps package structure to file system path relative to project root.
 */
fun getProductPropertiesSourceFile(clazz: Class<*>, @Suppress("UNUSED_PARAMETER") projectRoot: Path): String {
  val className = clazz.name
  
  // Map package to directory structure
  // org.jetbrains.intellij.build.IdeaUltimateProperties -> build/src/org/jetbrains/intellij/build/IdeaUltimateProperties.kt
  // org.jetbrains.intellij.build.goland.GoLandProperties -> goland/intellij-go-build/src/org/jetbrains/intellij/build/goland/GoLandProperties.kt
  
  val packagePath = className.replace('.', '/') + ".kt"
  
  // Handle special cases based on package
  return when {
    className.startsWith("org.jetbrains.intellij.build.goland.") ->
      "goland/intellij-go-build/src/$packagePath"
    className.startsWith("org.jetbrains.intellij.build.clion.") ->
      "CIDR/clion-build/src/$packagePath"
    className.startsWith("com.jetbrains.rider.build.") ->
      "rider/build/src/$packagePath"
    className.startsWith("org.jetbrains.intellij.build.dataGrip.") ->
      "dbe/build/src/$packagePath"
    className.startsWith("org.jetbrains.intellij.build.") && !className.contains("community") ->
      "build/src/$packagePath"
    className.startsWith("org.jetbrains.intellij.build.") ->
      "community/build/src/$packagePath"
    else -> packagePath // Fallback to package path
  }
}

// kotlinx.serialization Json instance for serializing data structures
private val kotlinxJson = Json {
  prettyPrint = false
  encodeDefaults = true
}

/**
 * Enriches products with calculated metrics.
 * Calculates totalModuleCount, directModuleCount, moduleSetCount, and uniqueModuleCount for each product.
 */
private fun enrichProductsWithMetrics(
  products: List<ProductSpec>,
  moduleSets: List<ModuleSet>
): List<ProductSpec> {
  return products.map { product ->
    val contentSpec = product.contentSpec
    if (contentSpec == null) {
      product // Return as-is if no contentSpec
    } else {
      // Calculate metrics
      val allModules = mutableSetOf<String>()

      // Collect modules from module sets
      for (msRef in contentSpec.moduleSets) {
        val modulesFromSet = collectAllModuleNamesFromSet(moduleSets, msRef.moduleSet.name)
        allModules.addAll(modulesFromSet)
      }

      // Add additional modules
      for (module in contentSpec.additionalModules) {
        allModules.add(module.name)
      }

      val totalModuleCount = allModules.size
      val directModuleCount = contentSpec.additionalModules.size
      val moduleSetCount = contentSpec.moduleSets.size
      val uniqueModuleCount = allModules.size // Same as total after deduplication

      product.copy(
        totalModuleCount = totalModuleCount,
        directModuleCount = directModuleCount,
        moduleSetCount = moduleSetCount,
        uniqueModuleCount = uniqueModuleCount
      )
    }
  }
}

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
        writeDuplicateAnalysis(gen, allModuleSets)
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
            val contentSpecJson = kotlinxJson.encodeToString(product.contentSpec)
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
          val moduleSetJson = kotlinxJson.encodeToString(moduleSetEntry.moduleSet)
          gen.writeFieldName("moduleSet")
          gen.writeRawValue(moduleSetJson)
          gen.writeEndObject()
        } else {
          gen.writeStringField("error", "Module set '$moduleSetName' not found")
        }
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
  writeDuplicateAnalysis(gen, allModuleSets)
  gen.writeEndObject()

  // Write product composition analysis
  gen.writeObjectFieldStart("productCompositionAnalysis")
  writeProductCompositionAnalysis(gen, products)
  gen.writeEndObject()

  // Write module distribution analysis
  gen.writeObjectFieldStart("moduleDistribution")
  writeModuleDistribution(gen, allModuleSets, products, projectRoot)
  gen.writeEndObject()

  // Write module set hierarchy
  gen.writeObjectFieldStart("moduleSetHierarchy")
  writeModuleSetHierarchy(gen, allModuleSets)
  gen.writeEndObject()

  // Write module usage index
  gen.writeObjectFieldStart("moduleUsageIndex")
  writeModuleUsageIndex(gen, allModuleSets, products)
  gen.writeEndObject()
  
  // Parse module locations for validation
  val moduleLocations = parseModulesXml(projectRoot)
  
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

/**
 * Writes a single module set to JSON.
 * Uses kotlinx.serialization to serialize the ModuleSet structure directly,
 * then embeds the raw JSON using writeRawValue().
 */
private fun writeModuleSet(
  gen: JsonGenerator,
  moduleSet: ModuleSet,
  location: String,
  sourceFilePath: String,
  allModuleSets: List<ModuleSet>
) {
  gen.writeStartObject()

  // Metadata fields
  gen.writeStringField("name", moduleSet.name)
  gen.writeStringField("location", location)
  gen.writeStringField("sourceFile", sourceFilePath)

  // Serialize ModuleSet using kotlinx.serialization and write raw JSON
  val moduleSetJson = kotlinxJson.encodeToString(moduleSet)
  gen.writeFieldName("moduleSet")
  gen.writeRawValue(moduleSetJson)

  // Add flattened list of all modules (including from nested sets)
  gen.writeArrayFieldStart("allModulesFlattened")
  val allModules = collectAllModuleNamesFromSet(allModuleSets, moduleSet.name)
  for (moduleName in allModules.sorted()) {
    gen.writeString(moduleName)
  }
  gen.writeEndArray()

  gen.writeEndObject()
}

/**
 * Writes a single product to JSON.
 * Uses kotlinx.serialization to serialize ProductModulesContentSpec directly,
 * providing complete DSL structure with all modules, overrides, and exclusions.
 */
private fun writeProduct(
  gen: JsonGenerator,
  product: ProductSpec
) {
  gen.writeStartObject()
  gen.writeStringField("name", product.name)
  gen.writeStringField("className", product.className)
  gen.writeStringField("sourceFile", product.sourceFile)
  
  if (product.pluginXmlPath != null) {
    gen.writeStringField("pluginXmlPath", product.pluginXmlPath)
  }
  
  // Serialize ProductModulesContentSpec using kotlinx.serialization and write raw JSON
  if (product.contentSpec != null) {
    val contentSpecJson = kotlinxJson.encodeToString(product.contentSpec)
    gen.writeFieldName("contentSpec")
    gen.writeRawValue(contentSpecJson)
  }
  
  // Build modules
  gen.writeArrayFieldStart("buildModules")
  for (buildModule in product.buildModules) {
    gen.writeString(buildModule)
  }
  gen.writeEndArray()

  // Metrics
  gen.writeNumberField("totalModuleCount", product.totalModuleCount)
  gen.writeNumberField("directModuleCount", product.directModuleCount)
  gen.writeNumberField("moduleSetCount", product.moduleSetCount)
  gen.writeNumberField("uniqueModuleCount", product.uniqueModuleCount)

  gen.writeEndObject()
}

/**
 * Writes duplicate analysis section.
 */
private fun writeDuplicateAnalysis(
  gen: JsonGenerator,
  allModuleSets: List<ModuleSetMetadata>
) {
  // Find modules that appear in multiple module sets
  val moduleToSets = mutableMapOf<String, MutableList<String>>()
  for ((moduleSet, _, _) in allModuleSets) {
    val allModules = collectAllModuleNames(moduleSet)
    for (moduleName in allModules) {
      moduleToSets.computeIfAbsent(moduleName) { mutableListOf() }.add(moduleSet.name)
    }
  }

  val duplicateModules = moduleToSets.filter { it.value.size > 1 }

  gen.writeArrayFieldStart("modulesInMultipleSets")
  for ((moduleName, setNames) in duplicateModules.entries.sortedBy { it.key }) {
    gen.writeStartObject()
    gen.writeStringField("moduleName", moduleName)

    gen.writeArrayFieldStart("appearsInSets")
    for (setName in setNames.sorted()) {
      gen.writeString(setName)
    }
    gen.writeEndArray()

    gen.writeEndObject()
  }
  gen.writeEndArray()

  // Set overlap analysis
  gen.writeArrayFieldStart("setOverlapAnalysis")
  for (i in allModuleSets.indices) {
    for (j in i + 1 until allModuleSets.size) {
      val (set1, _, _) = allModuleSets[i]
      val (set2, _, _) = allModuleSets[j]

      val modules1 = collectAllModuleNames(set1)
      val modules2 = collectAllModuleNames(set2)

      val overlap = modules1.intersect(modules2)
      if (overlap.size > 5) { // Only report significant overlaps
        val uniqueToSet1 = modules1 - modules2
        val uniqueToSet2 = modules2 - modules1

        gen.writeStartObject()
        gen.writeStringField("set1", set1.name)
        gen.writeStringField("set2", set2.name)
        gen.writeNumberField("overlapCount", overlap.size)
        gen.writeNumberField("set1TotalModules", modules1.size)
        gen.writeNumberField("set2TotalModules", modules2.size)
        
        val overlapPercentage = (overlap.size.toDouble() / minOf(modules1.size, modules2.size)) * 100
        gen.writeNumberField("overlapPercentage", overlapPercentage)

        if (uniqueToSet1.isNotEmpty()) {
          gen.writeArrayFieldStart("uniqueToSet1")
          for (moduleName in uniqueToSet1.sorted().take(10)) { // Limit to first 10
            gen.writeString(moduleName)
          }
          gen.writeEndArray()
        }

        if (uniqueToSet2.isNotEmpty()) {
          gen.writeArrayFieldStart("uniqueToSet2")
          for (moduleName in uniqueToSet2.sorted().take(10)) { // Limit to first 10
            gen.writeString(moduleName)
          }
          gen.writeEndArray()
        }

        gen.writeEndObject()
      }
    }
  }
  gen.writeEndArray()
}

/**
 * Analyzes product composition graphs and writes insights to JSON.
 * Provides statistics and recommendations for each product based on its composition.
 */
private fun writeProductCompositionAnalysis(
  gen: JsonGenerator,
  products: List<ProductSpec>
) {
  gen.writeArrayFieldStart("products")

  for (product in products) {
    val contentSpec = product.contentSpec ?: continue
    val compositionGraph = contentSpec.compositionGraph

    if (compositionGraph.isEmpty()) continue

    gen.writeStartObject()
    gen.writeStringField("productName", product.name)

    // Count composition types
    val typeCounts = compositionGraph.groupBy { it.type }.mapValues { it.value.size }
    gen.writeObjectFieldStart("compositionCounts")
    for ((type, count) in typeCounts.entries.sortedBy { it.key.name }) {
      gen.writeNumberField(type.name.lowercase(), count)
    }
    gen.writeEndObject()

    // Total operations
    gen.writeNumberField("totalCompositionOperations", compositionGraph.size)

    // List module set references
    val moduleSetRefs = compositionGraph.filter { it.type == CompositionType.MODULE_SET_REF }
    if (moduleSetRefs.isNotEmpty()) {
      gen.writeArrayFieldStart("moduleSetReferences")
      for (ref in moduleSetRefs) {
        gen.writeStartObject()
        gen.writeStringField("name", ref.reference ?: "unknown")
        gen.writeArrayFieldStart("path")
        for (pathItem in ref.path) {
          gen.writeString(pathItem)
        }
        gen.writeEndArray()
        gen.writeEndObject()
      }
      gen.writeEndArray()
    }

    // List inline spec includes
    val inlineSpecs = compositionGraph.filter { it.type == CompositionType.INLINE_SPEC }
    if (inlineSpecs.isNotEmpty()) {
      gen.writeArrayFieldStart("inlineSpecIncludes")
      for (spec in inlineSpecs) {
        gen.writeStartObject()
        gen.writeStringField("reference", spec.reference ?: "unknown")
        if (spec.sourceLocation != null) {
          gen.writeStringField("sourceFile", spec.sourceLocation)
        }
        gen.writeEndObject()
      }
      gen.writeEndArray()
    }

    gen.writeEndObject()
  }

  gen.writeEndArray()
}

/**
 * Writes module distribution analysis.
 * For each module, lists which module sets and products use it, plus location information.
 * Replaces TypeScript analyzeModuleDistribution() function.
 * 
 * Output format: { "moduleName": { "inModuleSets": [...], "inProducts": [...], "location": "...", "imlPath": "..." } }
 */
private fun writeModuleDistribution(
  gen: JsonGenerator,
  allModuleSets: List<ModuleSetMetadata>,
  products: List<ProductSpec>,
  projectRoot: Path
) {
  // Parse module locations from .idea/modules.xml
  val moduleLocations = parseModulesXml(projectRoot)
  
  // Build module → {inModuleSets: [], inProducts: []} mapping
  val moduleMap = mutableMapOf<String, ModuleDistributionInfo>()

  // Collect modules from module sets (recursively including nested sets)
  for ((moduleSet, _, _) in allModuleSets) {
    visitAllModules(moduleSet) { module ->
      val info = moduleMap.computeIfAbsent(module.name) { ModuleDistributionInfo() }
      info.inModuleSets.add(moduleSet.name)
    }
  }

  // Collect modules from products
  for (product in products) {
    val contentSpec = product.contentSpec ?: continue
    
    // Collect all modules used by this product (from module sets + additional modules)
    val allModulesInProduct = mutableSetOf<String>()
    
    // Collect from module sets (recursively)
    for (msRef in contentSpec.moduleSets) {
      val modulesFromSet = collectAllModuleNamesFromSet(allModuleSets.map { it.moduleSet }, msRef.moduleSet.name)
      allModulesInProduct.addAll(modulesFromSet)
    }
    
    // Add additional modules
    for (module in contentSpec.additionalModules) {
      allModulesInProduct.add(module.name)
    }
    
    // Add to module map
    for (moduleName in allModulesInProduct) {
      val info = moduleMap.computeIfAbsent(moduleName) { ModuleDistributionInfo() }
      if (product.name !in info.inProducts) {
        info.inProducts.add(product.name)
      }
    }
  }

  // Set location information from .idea/modules.xml
  for ((moduleName, info) in moduleMap) {
    val locationInfo = moduleLocations[moduleName]
    if (locationInfo != null) {
      info.location = locationInfo.location
      info.imlPath = locationInfo.imlPath
    }
  }
  
  // Write JSON as object (not array) for direct access by module name
  for ((moduleName, info) in moduleMap.entries.sortedBy { it.key }) {
    gen.writeObjectFieldStart(moduleName)
    
    gen.writeArrayFieldStart("inModuleSets")
    for (setName in info.inModuleSets.sorted()) {
      gen.writeString(setName)
    }
    gen.writeEndArray()
    
    gen.writeArrayFieldStart("inProducts")
    for (productName in info.inProducts.sorted()) {
      gen.writeString(productName)
    }
    gen.writeEndArray()
    
    gen.writeStringField("location", info.location)
    if (info.imlPath != null) {
      gen.writeStringField("imlPath", info.imlPath)
    }
    
    gen.writeEndObject()
  }
}

/**
 * Helper data class for module distribution analysis.
 */
private data class ModuleDistributionInfo(
  val inModuleSets: MutableList<String> = mutableListOf(),
  val inProducts: MutableList<String> = mutableListOf(),
  var location: String = "unknown",  // "community", "ultimate", or "unknown"
  var imlPath: String? = null
)

/**
 * Writes module set hierarchy analysis.
 * For each module set, lists what it includes and what includes it.
 * Replaces TypeScript buildModuleSetHierarchy() function.
 * 
 * Output format: { "setName": { "includes": [...], "includedBy": [...], "moduleCount": N } }
 */
private fun writeModuleSetHierarchy(
  gen: JsonGenerator,
  allModuleSets: List<ModuleSetMetadata>
) {
  // Build hierarchy map: moduleSetName → {includes: [], includedBy: [], moduleCount: N}
  val hierarchy = mutableMapOf<String, ModuleSetHierarchyInfo>()

  // First pass: build includes and module counts
  for ((moduleSet, _, _) in allModuleSets) {
    val info = ModuleSetHierarchyInfo(
      includes = moduleSet.nestedSets.map { it.name },
      moduleCount = moduleSet.modules.size
    )
    hierarchy[moduleSet.name] = info
  }

  // Second pass: build reverse references (includedBy)
  for ((moduleSet, _, _) in allModuleSets) {
    for (nestedSet in moduleSet.nestedSets) {
      hierarchy[nestedSet.name]?.includedBy?.add(moduleSet.name)
    }
  }

  // Write JSON as flat object (no "moduleSets" wrapper) for direct access
  for ((setName, info) in hierarchy.entries.sortedBy { it.key }) {
    gen.writeObjectFieldStart(setName)
    
    gen.writeArrayFieldStart("includes")
    for (includedSet in info.includes.sorted()) {
      gen.writeString(includedSet)
    }
    gen.writeEndArray()
    
    gen.writeArrayFieldStart("includedBy")
    for (parentSet in info.includedBy.sorted()) {
      gen.writeString(parentSet)
    }
    gen.writeEndArray()
    
    gen.writeNumberField("moduleCount", info.moduleCount)
    
    gen.writeEndObject()
  }
}

/**
 * Helper data class for module set hierarchy analysis.
 */
private data class ModuleSetHierarchyInfo(
  val includes: List<String>,
  val includedBy: MutableList<String> = mutableListOf(),
  val moduleCount: Int
)

/**
 * Writes module usage index.
 * For each module, provides complete information about where it's used and how to navigate to it.
 * Replaces TypeScript findModuleUsages() function.
 */
private fun writeModuleUsageIndex(
  gen: JsonGenerator,
  allModuleSets: List<ModuleSetMetadata>,
  products: List<ProductSpec>
) {
  // Build comprehensive usage index
  val usageIndex = mutableMapOf<String, ModuleUsageInfo>()

  // Collect from module sets (recursively including nested sets)
  for ((moduleSet, location, sourceFile) in allModuleSets) {
    visitAllModules(moduleSet) { module ->
      val info = usageIndex.computeIfAbsent(module.name) { ModuleUsageInfo() }
      info.moduleSets.add(ModuleSetReference(moduleSet.name, location, sourceFile))
    }
  }

  // Collect from products
  for (product in products) {
    val contentSpec = product.contentSpec ?: continue
    
    // Collect all modules used by this product
    val allModulesInProduct = mutableSetOf<String>()
    
    // From module sets
    for (msRef in contentSpec.moduleSets) {
      val modulesFromSet = collectAllModuleNamesFromSet(allModuleSets.map { it.moduleSet }, msRef.moduleSet.name)
      allModulesInProduct.addAll(modulesFromSet)
    }
    
    // From additional modules
    for (module in contentSpec.additionalModules) {
      allModulesInProduct.add(module.name)
    }
    
    // Add to index
    for (moduleName in allModulesInProduct) {
      val info = usageIndex.computeIfAbsent(moduleName) { ModuleUsageInfo() }
      info.products.add(ProductReference(product.name, product.sourceFile))
    }
  }

  // Write JSON
  gen.writeObjectFieldStart("modules")
  for ((moduleName, info) in usageIndex.entries.sortedBy { it.key }) {
    gen.writeObjectFieldStart(moduleName)
    
    // Module sets that include this module
    gen.writeArrayFieldStart("moduleSets")
    for (msRef in info.moduleSets.sortedBy { it.name }) {
      gen.writeStartObject()
      gen.writeStringField("name", msRef.name)
      gen.writeStringField("location", msRef.location)
      gen.writeStringField("sourceFile", msRef.sourceFile)
      gen.writeEndObject()
    }
    gen.writeEndArray()
    
    // Products that use this module
    gen.writeArrayFieldStart("products")
    for (prodRef in info.products.sortedBy { it.name }) {
      gen.writeStartObject()
      gen.writeStringField("name", prodRef.name)
      gen.writeStringField("sourceFile", prodRef.sourceFile)
      gen.writeEndObject()
    }
    gen.writeEndArray()
    
    gen.writeEndObject()
  }
  gen.writeEndObject()
}

/**
 * Helper data classes for module usage index.
 */
private data class ModuleUsageInfo(
  val moduleSets: MutableList<ModuleSetReference> = mutableListOf(),
  val products: MutableList<ProductReference> = mutableListOf()
)

private data class ModuleSetReference(
  val name: String,
  val location: String,
  val sourceFile: String
)

private data class ProductReference(
  val name: String,
  val sourceFile: String
)

/**
 * Recursively collects all module names from a module set and its nested sets.
 * Helper function for module distribution and usage analysis.
 */
private fun collectAllModuleNamesFromSet(
  moduleSets: List<ModuleSet>,
  setName: String,
  visited: MutableSet<String> = mutableSetOf()
): Set<String> {
  if (setName in visited) return emptySet()
  visited.add(setName)

  val moduleSet = moduleSets.firstOrNull { it.name == setName } ?: return emptySet()
  
  val allModules = moduleSet.modules.map { it.name }.toMutableSet()
  
  // Recursively collect from nested sets
  for (nestedSet in moduleSet.nestedSets) {
    allModules.addAll(collectAllModuleNamesFromSet(moduleSets, nestedSet.name, visited))
  }
  
  return allModules
}

/**
 * Violation when a community product uses ultimate modules.
 */
private data class CommunityProductViolation(
  val product: String,
  val productFile: String,
  val moduleSet: String,
  val moduleSetFile: String,
  val ultimateModules: List<String>,
  val communityModulesCount: Int,
  val unknownModulesCount: Int,
  val totalModulesCount: Int
)

/**
 * Violation when a module set is in the wrong location (community vs ultimate).
 */
private data class ModuleSetLocationViolation(
  val moduleSet: String,
  val file: String,
  val issue: String,  // "community_contains_ultimate" or "ultimate_contains_only_community"
  val ultimateModules: List<String>? = null,
  val communityModules: List<String>? = null,
  val communityModulesCount: Int? = null,
  val ultimateModulesCount: Int? = null,
  val unknownModulesCount: Int,
  val suggestion: String
)

/**
 * Similarity between two products based on module set overlap.
 * Used for identifying merge candidates and refactoring opportunities.
 */
private data class ProductSimilarityPair(
  val product1: String,
  val product2: String,
  val similarity: Double,
  val moduleSetSimilarity: Double,
  val sharedModuleSets: List<String>,
  val uniqueToProduct1: List<String>,
  val uniqueToProduct2: List<String>
)

/**
 * Overlap between two module sets.
 * Correctly identifies intentional nested set inclusions vs actual duplications.
 * Intentional nesting (e.g., libraries includes libraries.core) is filtered out.
 */
private data class ModuleSetOverlap(
  val moduleSet1: String,
  val moduleSet2: String,
  val location1: String,
  val location2: String,
  val relationship: String,  // "overlap", "subset", "superset"
  val overlapPercent: Int,
  val sharedModules: Int,
  val totalModules1: Int,
  val totalModules2: Int,
  val recommendation: String
)

/**
 * Suggestion for module set unification (merge, inline, factor, split).
 * Generated by analyzing overlap, product similarity, and module set usage patterns.
 */
private data class UnificationSuggestion(
  val priority: String,  // "high", "medium", "low"
  val strategy: String,  // "merge", "inline", "factor", "split"
  val type: String?,  // For merge: "subset", "superset", "high-overlap"
  val moduleSet: String?,  // For inline/split: single module set
  val moduleSet1: String?,  // For merge: first module set
  val moduleSet2: String?,  // For merge: second module set
  val products: List<String>?,  // For factor: products with shared sets
  val sharedModuleSets: List<String>?,  // For factor: shared module sets
  val reason: String,
  val impact: Map<String, Any>
)

/**
 * Impact analysis result for merging, moving, or inlining module sets.
 * Used to assess safety and predict consequences before refactoring.
 */
private data class MergeImpactResult(
  val operation: String,  // "merge", "move", "inline"
  val sourceSet: String,
  val targetSet: String?,
  val productsUsingSource: List<String>,
  val productsUsingTarget: List<String>,
  val productsThatWouldChange: List<String>,
  val sizeImpact: Map<String, Int>,
  val violations: List<Map<String, Any>>,
  val recommendation: String,
  val safe: Boolean
)

/**
 * Validates that community products don't use ultimate modules.
 * This enforces the architectural constraint that community products must only use community modules.
 * 
 * @param products List of all products
 * @param allModuleSets List of all module sets with metadata
 * @param moduleLocations Map of module names to their locations (from .idea/modules.xml)
 * @param projectRoot Project root path for constructing file paths
 * @return List of violations
 */
private fun validateCommunityProducts(
  products: List<ProductSpec>,
  allModuleSets: List<ModuleSetMetadata>,
  moduleLocations: Map<String, ModuleLocationInfo>,
  projectRoot: Path
): List<CommunityProductViolation> {
  val violations = mutableListOf<CommunityProductViolation>()
  
  for (product in products) {
    if (product.pluginXmlPath == null || product.contentSpec == null) continue
    
    // Check if product is in community
    val productFile = projectRoot.resolve(product.pluginXmlPath).toString()
    val isCommunityProduct = productFile.contains("/community/")
    
    if (isCommunityProduct) {
      // Check each module set used by this product
      for (msRef in product.contentSpec.moduleSets) {
        val setName = msRef.moduleSet.name
        val msEntry = allModuleSets.firstOrNull { it.moduleSet.name == setName } ?: continue
        
        // Get ALL modules including those from nested sets (pre-calculated)
        val allModules = collectAllModuleNamesFromSet(allModuleSets.map { it.moduleSet }, setName)
        
        // Find ultimate modules
        val ultimateModules = mutableListOf<String>()
        var communityModulesCount = 0
        var unknownModulesCount = 0
        
        for (moduleName in allModules) {
          val locationInfo = moduleLocations[moduleName]
          when (locationInfo?.location) {
            "ultimate" -> ultimateModules.add(moduleName)
            "community" -> communityModulesCount++
            else -> unknownModulesCount++
          }
        }
        
        if (ultimateModules.isNotEmpty()) {
          violations.add(CommunityProductViolation(
            product = product.name,
            productFile = productFile,
            moduleSet = setName,
            moduleSetFile = projectRoot.resolve(msEntry.sourceFile).toString(),
            ultimateModules = ultimateModules,
            communityModulesCount = communityModulesCount,
            unknownModulesCount = unknownModulesCount,
            totalModulesCount = allModules.size
          ))
        }
      }
    }
  }
  
  return violations
}

/**
 * Validates that module sets are in correct locations (community vs ultimate).
 * Reports violations where:
 * - A module set in community/ contains ultimate modules
 * - A module set in ultimate/ contains only community modules
 * 
 * @param allModuleSets List of all module sets with metadata
 * @param moduleLocations Map of module names to their locations (from .idea/modules.xml)
 * @param projectRoot Project root path for constructing file paths
 * @return List of violations
 */
private fun validateModuleSetLocations(
  allModuleSets: List<ModuleSetMetadata>,
  moduleLocations: Map<String, ModuleLocationInfo>,
  projectRoot: Path
): List<ModuleSetLocationViolation> {
  val violations = mutableListOf<ModuleSetLocationViolation>()
  
  for (msEntry in allModuleSets) {
    val ms = msEntry.moduleSet
    val setFile = projectRoot.resolve(msEntry.sourceFile).toString()
    val isInCommunity = setFile.contains("/community/")
    val isInUltimate = setFile.contains("/ultimate/")
    
    // Count community vs ultimate modules in this set
    val ultimateModules = mutableListOf<String>()
    val communityModules = mutableListOf<String>()
    var unknownCount = 0
    
    for (module in ms.modules) {
      val locationInfo = moduleLocations[module.name]
      when (locationInfo?.location) {
        "ultimate" -> ultimateModules.add(module.name)
        "community" -> communityModules.add(module.name)
        else -> unknownCount++
      }
    }
    
    // Violation: Module set in community/ contains ultimate modules
    if (isInCommunity && ultimateModules.isNotEmpty()) {
      violations.add(ModuleSetLocationViolation(
        moduleSet = ms.name,
        file = setFile,
        issue = "community_contains_ultimate",
        ultimateModules = ultimateModules,
        communityModulesCount = communityModules.size,
        unknownModulesCount = unknownCount,
        suggestion = "Move to ultimate/platform-ultimate/resources/META-INF/"
      ))
    }
    
    // Warning: Module set in ultimate/ contains only community modules
    if (isInUltimate && ultimateModules.isEmpty() && communityModules.isNotEmpty()) {
      violations.add(ModuleSetLocationViolation(
        moduleSet = ms.name,
        file = setFile,
        issue = "ultimate_contains_only_community",
        ultimateModulesCount = 0,
        communityModules = communityModules,
        unknownModulesCount = unknownCount,
        suggestion = "Consider if this should be in community/"
      ))
    }
  }
  
  return violations
}

/**
 * Recursively collects all nested set names (direct + transitive) from a module set.
 *
 * For example, if essential includes libraries, and libraries includes libraries.core,
 * this returns {"libraries", "libraries.core", ...} for essential.
 *
 * @param allModuleSets All module sets to search in
 * @param startSetName The module set to start collecting from
 * @param visited Set of already visited module sets to prevent infinite recursion
 * @return Set of all nested set names (direct and transitive)
 */
private fun collectAllNestedSetNames(
  allModuleSets: List<ModuleSet>,
  startSetName: String,
  visited: MutableSet<String> = mutableSetOf()
): Set<String> {
  if (visited.contains(startSetName)) return emptySet()
  visited.add(startSetName)

  val startSet = allModuleSets.firstOrNull { it.name == startSetName } ?: return emptySet()
  val result = mutableSetOf<String>()

  for (nestedSet in startSet.nestedSets) {
    result.add(nestedSet.name)
    // Recursively collect nested sets from this nested set
    result.addAll(collectAllNestedSetNames(allModuleSets, nestedSet.name, visited))
  }

  return result
}

/**
 * Detects overlapping or redundant module sets.
 * CRITICAL FIX: Filters out intentional nested set inclusions (e.g., libraries ⊃ libraries.core).
 * ENHANCED: Now checks TRANSITIVE nested relationships (e.g., essential → libraries → libraries.core).
 * Only reports actual duplications, not designed composition patterns.
 *
 * @param allModuleSets List of all module sets with metadata
 * @param minOverlapPercent Minimum overlap percentage (0-100) to include in results
 * @return List of overlapping module set pairs sorted by overlap percentage (descending)
 */
private fun detectModuleSetOverlap(
  allModuleSets: List<ModuleSetMetadata>,
  minOverlapPercent: Int = 50
): List<ModuleSetOverlap> {
  val overlaps = mutableListOf<ModuleSetOverlap>()
  val moduleSetsList = allModuleSets.map { it.moduleSet }

  for (i in allModuleSets.indices) {
    for (j in i + 1 until allModuleSets.size) {
      val ms1 = allModuleSets[i]
      val ms2 = allModuleSets[j]

      // ✅ CRITICAL FIX: Skip if one explicitly includes the other as a nested set
      // This prevents false positives like "libraries overlaps with libraries.core"
      // when libraries explicitly includes libraries.core by design
      //
      // ✅ ENHANCED: Now checks TRANSITIVE relationships too!
      // Example: essential → libraries → libraries.core
      // This prevents false positive for "essential overlaps with libraries.core"
      val ms1AllNestedSetNames = collectAllNestedSetNames(moduleSetsList, ms1.moduleSet.name)
      val ms2AllNestedSetNames = collectAllNestedSetNames(moduleSetsList, ms2.moduleSet.name)

      if (ms1AllNestedSetNames.contains(ms2.moduleSet.name) ||
          ms2AllNestedSetNames.contains(ms1.moduleSet.name)) {
        continue  // Intentional composition via nesting (direct or transitive), not duplication!
      }
      
      // Calculate overlap based on direct modules only (not nested)
      val modules1 = ms1.moduleSet.modules.map { it.name }.toSet()
      val modules2 = ms2.moduleSet.modules.map { it.name }.toSet()
      
      val intersection = modules1.intersect(modules2)
      if (intersection.isEmpty()) continue
      
      val union = modules1.union(modules2)
      val overlapPercent = (intersection.size * 100) / union.size
      
      if (overlapPercent >= minOverlapPercent) {
        val relationship = when {
          intersection.size == modules1.size -> "subset"  // ms1 ⊂ ms2
          intersection.size == modules2.size -> "superset"  // ms1 ⊃ ms2
          else -> "overlap"
        }
        
        overlaps.add(ModuleSetOverlap(
          moduleSet1 = ms1.moduleSet.name,
          moduleSet2 = ms2.moduleSet.name,
          location1 = ms1.location,
          location2 = ms2.location,
          relationship = relationship,
          overlapPercent = overlapPercent,
          sharedModules = intersection.size,
          totalModules1 = modules1.size,
          totalModules2 = modules2.size,
          recommendation = generateOverlapRecommendation(ms1, ms2, relationship, overlapPercent)
        ))
      }
    }
  }
  
  return overlaps.sortedByDescending { it.overlapPercent }
}

/**
 * Generates recommendation for overlapping module sets.
 */
private fun generateOverlapRecommendation(
  ms1: ModuleSetMetadata,
  ms2: ModuleSetMetadata,
  relationship: String,
  overlapPercent: Int
): String {
  return when (relationship) {
    "subset" -> "${ms1.moduleSet.name} is fully contained in ${ms2.moduleSet.name}. Consider removing ${ms1.moduleSet.name}."
    "superset" -> "${ms2.moduleSet.name} is fully contained in ${ms1.moduleSet.name}. Consider removing ${ms2.moduleSet.name}."
    else -> if (overlapPercent >= 80) {
      "High overlap ($overlapPercent%). Review if modules should be reorganized."
    } else {
      "Moderate overlap ($overlapPercent%). Consider extracting shared modules."
    }
  }
}

/**
 * Analyzes similarity between products based on module set overlap.
 * Used to identify products with similar compositions for potential refactoring.
 * 
 * @param products List of all products
 * @param similarityThreshold Minimum similarity (0.0 to 1.0) to include in results
 * @return List of similar product pairs sorted by similarity (descending)
 */
private fun analyzeProductSimilarity(
  products: List<ProductSpec>,
  similarityThreshold: Double = 0.7
): List<ProductSimilarityPair> {
  val pairs = mutableListOf<ProductSimilarityPair>()
  val productsWithContent = products.filter { it.contentSpec != null }
  
  for (i in productsWithContent.indices) {
    for (j in i + 1 until productsWithContent.size) {
      val p1 = productsWithContent[i]
      val p2 = productsWithContent[j]
      
      val sets1 = p1.contentSpec!!.moduleSets.map { it.moduleSet.name }.toSet()
      val sets2 = p2.contentSpec!!.moduleSets.map { it.moduleSet.name }.toSet()
      
      val shared = sets1.intersect(sets2)
      val union = sets1.union(sets2)
      val similarity = if (union.isNotEmpty()) shared.size.toDouble() / union.size else 0.0
      
      if (similarity >= similarityThreshold) {
        pairs.add(ProductSimilarityPair(
          product1 = p1.name,
          product2 = p2.name,
          similarity = similarity,
          moduleSetSimilarity = similarity,
          sharedModuleSets = shared.toList().sorted(),
          uniqueToProduct1 = sets1.minus(sets2).toList().sorted(),
          uniqueToProduct2 = sets2.minus(sets1).toList().sorted()
        ))
      }
    }
  }
  
  return pairs.sortedByDescending { it.similarity }
}

/**
 * Suggests module set unification opportunities based on overlap, similarity, and usage patterns.
 * 
 * Strategies:
 * - merge: Combine overlapping module sets (especially subsets/supersets)
 * - inline: Inline rarely-used small module sets directly into products
 * - factor: Extract common patterns from similar products
 * - split: Split oversized module sets for better maintainability
 * 
 * @param allModuleSets All module sets with metadata
 * @param products All products
 * @param overlaps Pre-calculated module set overlaps
 * @param similarityPairs Pre-calculated product similarity pairs
 * @param maxSuggestions Maximum number of suggestions to return
 * @param strategy Filter by strategy: "merge", "inline", "factor", "split", or "all"
 * @return List of suggestions sorted by priority
 */
private fun suggestModuleSetUnification(
  allModuleSets: List<ModuleSetMetadata>,
  products: List<ProductSpec>,
  overlaps: List<ModuleSetOverlap>,
  similarityPairs: List<ProductSimilarityPair>,
  maxSuggestions: Int = 10,
  strategy: String = "all"
): List<UnificationSuggestion> {
  val suggestions = mutableListOf<UnificationSuggestion>()
  
  // Strategy 1: Merge overlapping module sets
  if (strategy == "merge" || strategy == "all") {
    for (overlap in overlaps) {
      if (overlap.relationship == "subset" || overlap.relationship == "superset") {
        suggestions.add(UnificationSuggestion(
          priority = "high",
          strategy = "merge",
          type = overlap.relationship,
          moduleSet = null,
          moduleSet1 = overlap.moduleSet1,
          moduleSet2 = overlap.moduleSet2,
          products = null,
          sharedModuleSets = null,
          reason = overlap.recommendation,
          impact = mapOf(
            "moduleSetsSaved" to 1,
            "overlapPercent" to overlap.overlapPercent
          )
        ))
      } else if (overlap.overlapPercent >= 80) {
        suggestions.add(UnificationSuggestion(
          priority = "medium",
          strategy = "merge",
          type = "high-overlap",
          moduleSet = null,
          moduleSet1 = overlap.moduleSet1,
          moduleSet2 = overlap.moduleSet2,
          products = null,
          sharedModuleSets = null,
          reason = overlap.recommendation,
          impact = mapOf("overlapPercent" to overlap.overlapPercent)
        ))
      }
    }
  }
  
  // Strategy 2: Find rarely-used module sets (inline candidates)
  if (strategy == "inline" || strategy == "all") {
    for (msEntry in allModuleSets) {
      val usedByProducts = products.filter { p ->
        p.contentSpec?.moduleSets?.any { it.moduleSet.name == msEntry.moduleSet.name } == true
      }
      
      // Use total module count (including nested sets) for inline candidate detection
      val totalModuleCount = collectAllModuleNames(msEntry.moduleSet).size
      if (usedByProducts.size <= 1 && totalModuleCount <= 5) {
        suggestions.add(UnificationSuggestion(
          priority = "low",
          strategy = "inline",
          type = null,
          moduleSet = msEntry.moduleSet.name,
          moduleSet1 = null,
          moduleSet2 = null,
          products = null,
          sharedModuleSets = null,
          reason = "Used by only ${usedByProducts.size} product(s) and contains only $totalModuleCount modules. Consider inlining into the product directly.",
          impact = mapOf(
            "moduleSetsSaved" to 1,
            "moduleCount" to totalModuleCount,
            "affectedProducts" to usedByProducts.map { it.name }
          )
        ))
      }
    }
  }
  
  // Strategy 3: Find common patterns (factoring opportunities)
  if (strategy == "factor" || strategy == "all") {
    for (pair in similarityPairs) {
      if (pair.sharedModuleSets.size >= 3) {
        suggestions.add(UnificationSuggestion(
          priority = "medium",
          strategy = "factor",
          type = null,
          moduleSet = null,
          moduleSet1 = null,
          moduleSet2 = null,
          products = listOf(pair.product1, pair.product2),
          sharedModuleSets = pair.sharedModuleSets,
          reason = "Products ${pair.product1} and ${pair.product2} share ${pair.sharedModuleSets.size} module sets (${(pair.similarity * 100).toInt()}% similarity). Consider creating a common base.",
          impact = mapOf(
            "similarity" to pair.similarity,
            "sharedModuleSets" to pair.sharedModuleSets.size
          )
        ))
      }
    }
  }
  
  // Strategy 4: Split large module sets
  if (strategy == "split" || strategy == "all") {
    for (msEntry in allModuleSets) {
      // Use total module count (including nested sets) for split suggestions
      val totalModuleCount = collectAllModuleNames(msEntry.moduleSet).size
      if (totalModuleCount > 200) {
        suggestions.add(UnificationSuggestion(
          priority = "low",
          strategy = "split",
          type = null,
          moduleSet = msEntry.moduleSet.name,
          moduleSet1 = null,
          moduleSet2 = null,
          products = null,
          sharedModuleSets = null,
          reason = "Module set contains $totalModuleCount modules. Consider splitting into smaller, more focused sets for better maintainability.",
          impact = mapOf("moduleCount" to totalModuleCount)
        ))
      }
    }
  }
  
  // Remove duplicates and sort by priority
  val uniqueSuggestions = mutableListOf<UnificationSuggestion>()
  val seen = mutableSetOf<String>()
  for (suggestion in suggestions) {
    val key = listOf(suggestion.strategy, suggestion.moduleSet1, suggestion.moduleSet2, suggestion.moduleSet).toString()
    if (!seen.contains(key)) {
      seen.add(key)
      uniqueSuggestions.add(suggestion)
    }
  }
  
  // Sort by priority: high > medium > low
  val priorityOrder = mapOf("high" to 3, "medium" to 2, "low" to 1)
  uniqueSuggestions.sortByDescending { priorityOrder[it.priority] ?: 0 }
  
  return uniqueSuggestions.take(maxSuggestions)
}

/**
 * Analyzes the impact of merging, moving, or inlining module sets.
 * Checks for violations, calculates size impact, and provides recommendations.
 * 
 * @param sourceSet Source module set name
 * @param targetSet Target module set name (null for inline operation)
 * @param operation Operation type: "merge", "move", or "inline"
 * @param allModuleSets All module sets with metadata
 * @param products All products
 * @return Impact analysis result
 */
private fun analyzeMergeImpact(
  sourceSet: String,
  targetSet: String?,
  operation: String,
  allModuleSets: List<ModuleSetMetadata>,
  products: List<ProductSpec>
): MergeImpactResult? {
  // Find source module set
  val sourceEntry = allModuleSets.firstOrNull { it.moduleSet.name == sourceSet }
  if (sourceEntry == null) {
    return null  // Error: source not found
  }
  
  // Find target module set (if applicable)
  var targetEntry: ModuleSetMetadata? = null
  if (targetSet != null) {
    targetEntry = allModuleSets.firstOrNull { it.moduleSet.name == targetSet }
    if (targetEntry == null) {
      return null  // Error: target not found
    }
  }
  
  // Find products using source
  val productsUsingSource = products.filter { p ->
    p.contentSpec?.moduleSets?.any { it.moduleSet.name == sourceSet } == true
  }
  
  // Find products using target
  val productsUsingTarget = if (targetSet != null) {
    products.filter { p ->
      p.contentSpec?.moduleSets?.any { it.moduleSet.name == targetSet } == true
    }
  } else {
    emptyList()
  }
  
  // Calculate module changes
  val sourceModules = sourceEntry.moduleSet.modules.map { it.name }.toSet()
  val targetModules = if (targetEntry != null) {
    targetEntry.moduleSet.modules.map { it.name }.toSet()
  } else {
    emptySet()
  }
  
  val newModules = sourceModules.minus(targetModules)
  val duplicateModules = sourceModules.intersect(targetModules)
  
  // Check for community/ultimate violations
  val violations = mutableListOf<Map<String, Any>>()
  if (operation == "merge" && targetEntry != null) {
    val sourceLocation = sourceEntry.location
    val targetLocation = targetEntry.location
    
    if (sourceLocation == "ultimate" && targetLocation == "community") {
      violations.add(mapOf(
        "type" to "location",
        "severity" to "error",
        "message" to "Cannot merge ultimate module set \"$sourceSet\" into community module set \"$targetSet\"",
        "fix" to "Move \"$targetSet\" to ultimate directory, or extract community modules from \"$sourceSet\""
      ))
    }
    
    // Check if any community products would gain ultimate modules
    val communityProductsUsingTarget = productsUsingTarget.filter { p ->
      val productSets = p.contentSpec?.moduleSets?.map { it.moduleSet.name } ?: emptyList()
      !productSets.contains("commercialIdeBase") && !productSets.contains("ide.ultimate")
    }
    
    if (sourceLocation == "ultimate" && communityProductsUsingTarget.isNotEmpty()) {
      violations.add(mapOf(
        "type" to "community-uses-ultimate",
        "severity" to "error",
        "message" to "Merging ultimate set \"$sourceSet\" into \"$targetSet\" would expose ultimate modules to ${communityProductsUsingTarget.size} community products",
        "affectedProducts" to communityProductsUsingTarget.map { it.name },
        "fix" to "Remove \"$targetSet\" from community products, or split ultimate modules from \"$sourceSet\""
      ))
    }
  }
  
  // Calculate size impact
  val sizeImpact = mapOf(
    "sourceModuleCount" to sourceModules.size,
    "targetModuleCount" to targetModules.size,
    "newModulesToTarget" to newModules.size,
    "duplicateModules" to duplicateModules.size,
    "resultingModuleCount" to targetModules.size + newModules.size
  )
  
  // Generate recommendation
  val recommendation = when {
    violations.isNotEmpty() -> "NOT RECOMMENDED: Operation would introduce violations. See violations for details."
    operation == "merge" && duplicateModules.isNotEmpty() -> 
      "CAUTION: ${duplicateModules.size} modules already exist in target. Merge would create no duplicates, but review if modules serve the same purpose."
    operation == "merge" && newModules.isNotEmpty() -> 
      "SAFE TO MERGE: Would add ${newModules.size} new modules to \"$targetSet\". ${productsUsingTarget.size} products using target would gain these modules."
    operation == "inline" -> 
      "SAFE TO INLINE: ${productsUsingSource.size} products using \"$sourceSet\" would directly include ${sourceModules.size} modules instead."
    else -> "Operation appears safe based on current analysis."
  }
  
  return MergeImpactResult(
    operation = operation,
    sourceSet = sourceSet,
    targetSet = targetSet,
    productsUsingSource = productsUsingSource.map { it.name },
    productsUsingTarget = productsUsingTarget.map { it.name },
    productsThatWouldChange = if (operation == "merge") {
      productsUsingTarget.map { it.name }
    } else {
      productsUsingSource.map { it.name }
    },
    sizeImpact = sizeImpact,
    violations = violations,
    recommendation = recommendation,
    safe = violations.isEmpty()
  )
}

/**
 * Writes community product validation violations to JSON.
 */
private fun writeCommunityProductViolations(
  gen: JsonGenerator,
  violations: List<CommunityProductViolation>
) {
  gen.writeArrayFieldStart("violations")
  for (violation in violations) {
    gen.writeStartObject()
    gen.writeStringField("product", violation.product)
    gen.writeStringField("productFile", violation.productFile)
    gen.writeStringField("moduleSet", violation.moduleSet)
    gen.writeStringField("moduleSetFile", violation.moduleSetFile)
    
    gen.writeArrayFieldStart("ultimateModules")
    for (module in violation.ultimateModules) {
      gen.writeString(module)
    }
    gen.writeEndArray()
    
    gen.writeNumberField("communityModulesCount", violation.communityModulesCount)
    gen.writeNumberField("unknownModulesCount", violation.unknownModulesCount)
    gen.writeNumberField("totalModulesCount", violation.totalModulesCount)
    gen.writeEndObject()
  }
  gen.writeEndArray()
  
  // Summary
  gen.writeObjectFieldStart("summary")
  gen.writeNumberField("totalViolations", violations.size)
  
  gen.writeArrayFieldStart("affectedProducts")
  for (product in violations.map { it.product }.distinct().sorted()) {
    gen.writeString(product)
  }
  gen.writeEndArray()
  
  gen.writeArrayFieldStart("affectedModuleSets")
  for (moduleSet in violations.map { it.moduleSet }.distinct().sorted()) {
    gen.writeString(moduleSet)
  }
  gen.writeEndArray()
  
  gen.writeEndObject()
}

/**
 * Writes module set location validation violations to JSON.
 */
private fun writeModuleSetLocationViolations(
  gen: JsonGenerator,
  violations: List<ModuleSetLocationViolation>
) {
  gen.writeArrayFieldStart("violations")
  for (violation in violations) {
    gen.writeStartObject()
    gen.writeStringField("moduleSet", violation.moduleSet)
    gen.writeStringField("file", violation.file)
    gen.writeStringField("issue", violation.issue)
    
    if (violation.ultimateModules != null) {
      gen.writeArrayFieldStart("ultimateModules")
      for (module in violation.ultimateModules) {
        gen.writeString(module)
      }
      gen.writeEndArray()
    }
    
    if (violation.communityModules != null) {
      gen.writeArrayFieldStart("communityModules")
      for (module in violation.communityModules) {
        gen.writeString(module)
      }
      gen.writeEndArray()
    }
    
    if (violation.communityModulesCount != null) {
      gen.writeNumberField("communityModulesCount", violation.communityModulesCount)
    }
    
    if (violation.ultimateModulesCount != null) {
      gen.writeNumberField("ultimateModulesCount", violation.ultimateModulesCount)
    }
    
    gen.writeNumberField("unknownModulesCount", violation.unknownModulesCount)
    gen.writeStringField("suggestion", violation.suggestion)
    gen.writeEndObject()
  }
  gen.writeEndArray()
  
  // Summary
  gen.writeObjectFieldStart("summary")
  gen.writeNumberField("totalViolations", violations.size)
  gen.writeNumberField("communityContainsUltimate", violations.count { it.issue == "community_contains_ultimate" })
  gen.writeNumberField("ultimateContainsOnlyCommunity", violations.count { it.issue == "ultimate_contains_only_community" })
  gen.writeEndObject()
}

/**
 * Writes product similarity analysis to JSON.
 * Includes similar product pairs and summary statistics.
 */
private fun writeProductSimilarityAnalysis(
  gen: JsonGenerator,
  pairs: List<ProductSimilarityPair>,
  threshold: Double
) {
  gen.writeArrayFieldStart("pairs")
  for (pair in pairs) {
    gen.writeStartObject()
    gen.writeStringField("product1", pair.product1)
    gen.writeStringField("product2", pair.product2)
    gen.writeNumberField("similarity", pair.similarity)
    gen.writeNumberField("moduleSetSimilarity", pair.moduleSetSimilarity)
    
    gen.writeArrayFieldStart("sharedModuleSets")
    for (setName in pair.sharedModuleSets) {
      gen.writeString(setName)
    }
    gen.writeEndArray()
    
    gen.writeArrayFieldStart("uniqueToProduct1")
    for (setName in pair.uniqueToProduct1) {
      gen.writeString(setName)
    }
    gen.writeEndArray()
    
    gen.writeArrayFieldStart("uniqueToProduct2")
    for (setName in pair.uniqueToProduct2) {
      gen.writeString(setName)
    }
    gen.writeEndArray()
    
    gen.writeEndObject()
  }
  gen.writeEndArray()
  
  gen.writeNumberField("totalPairs", pairs.size)
  gen.writeNumberField("threshold", threshold)
  gen.writeStringField("summary", "Found ${pairs.size} product pairs with ≥${(threshold * 100).toInt()}% similarity")
}

/**
 * Writes module set overlap analysis to JSON.
 * Includes overlapping module set pairs and summary statistics.
 * Note: Intentional nested set inclusions are already filtered out during analysis.
 */
private fun writeModuleSetOverlapAnalysis(
  gen: JsonGenerator,
  overlaps: List<ModuleSetOverlap>,
  minPercent: Int
) {
  gen.writeArrayFieldStart("overlaps")
  for (overlap in overlaps) {
    gen.writeStartObject()
    gen.writeStringField("moduleSet1", overlap.moduleSet1)
    gen.writeStringField("moduleSet2", overlap.moduleSet2)
    gen.writeStringField("location1", overlap.location1)
    gen.writeStringField("location2", overlap.location2)
    gen.writeStringField("relationship", overlap.relationship)
    gen.writeNumberField("overlapPercent", overlap.overlapPercent)
    gen.writeNumberField("sharedModules", overlap.sharedModules)
    gen.writeNumberField("totalModules1", overlap.totalModules1)
    gen.writeNumberField("totalModules2", overlap.totalModules2)
    gen.writeStringField("recommendation", overlap.recommendation)
    gen.writeEndObject()
  }
  gen.writeEndArray()
  
  gen.writeNumberField("count", overlaps.size)
  gen.writeStringField("summary", "Found ${overlaps.size} module set pairs with ≥$minPercent% overlap (excluding intentional nesting)")
}

/**
 * Writes module set unification suggestions to JSON.
 * Includes suggestions for merge, inline, factor, and split strategies.
 */
private fun writeUnificationSuggestions(
  gen: JsonGenerator,
  suggestions: List<UnificationSuggestion>
) {
  gen.writeArrayFieldStart("suggestions")
  for (suggestion in suggestions) {
    gen.writeStartObject()
    gen.writeStringField("priority", suggestion.priority)
    gen.writeStringField("strategy", suggestion.strategy)
    
    if (suggestion.type != null) {
      gen.writeStringField("type", suggestion.type)
    }
    if (suggestion.moduleSet != null) {
      gen.writeStringField("moduleSet", suggestion.moduleSet)
    }
    if (suggestion.moduleSet1 != null) {
      gen.writeStringField("moduleSet1", suggestion.moduleSet1)
    }
    if (suggestion.moduleSet2 != null) {
      gen.writeStringField("moduleSet2", suggestion.moduleSet2)
    }
    if (suggestion.products != null) {
      gen.writeArrayFieldStart("products")
      for (product in suggestion.products) {
        gen.writeString(product)
      }
      gen.writeEndArray()
    }
    if (suggestion.sharedModuleSets != null) {
      gen.writeArrayFieldStart("sharedModuleSets")
      for (setName in suggestion.sharedModuleSets) {
        gen.writeString(setName)
      }
      gen.writeEndArray()
    }
    
    gen.writeStringField("reason", suggestion.reason)
    
    gen.writeObjectFieldStart("impact")
    for ((key, value) in suggestion.impact) {
      when (value) {
        is Number -> gen.writeNumberField(key, value.toDouble())
        is String -> gen.writeStringField(key, value)
        is List<*> -> {
          gen.writeArrayFieldStart(key)
          for (item in value) {
            gen.writeString(item.toString())
          }
          gen.writeEndArray()
        }
      }
    }
    gen.writeEndObject()
    
    gen.writeEndObject()
  }
  gen.writeEndArray()
  
  gen.writeNumberField("totalSuggestions", suggestions.size)
  gen.writeStringField("summary", "Found ${suggestions.size} unification opportunities")
}

/**
 * Writes merge impact analysis to JSON.
 * Includes products affected, size impact, violations, and recommendation.
 */
private fun writeMergeImpactAnalysis(
  gen: JsonGenerator,
  impact: MergeImpactResult
) {
  gen.writeStringField("operation", impact.operation)
  gen.writeStringField("sourceSet", impact.sourceSet)
  if (impact.targetSet != null) {
    gen.writeStringField("targetSet", impact.targetSet)
  }
  
  gen.writeArrayFieldStart("productsUsingSource")
  for (product in impact.productsUsingSource) {
    gen.writeString(product)
  }
  gen.writeEndArray()
  
  gen.writeArrayFieldStart("productsUsingTarget")
  for (product in impact.productsUsingTarget) {
    gen.writeString(product)
  }
  gen.writeEndArray()
  
  gen.writeArrayFieldStart("productsThatWouldChange")
  for (product in impact.productsThatWouldChange) {
    gen.writeString(product)
  }
  gen.writeEndArray()
  
  gen.writeObjectFieldStart("sizeImpact")
  for ((key, value) in impact.sizeImpact) {
    gen.writeNumberField(key, value)
  }
  gen.writeEndObject()
  
  gen.writeArrayFieldStart("violations")
  for (violation in impact.violations) {
    gen.writeStartObject()
    for ((key, value) in violation) {
      when (value) {
        is String -> gen.writeStringField(key, value)
        is Number -> gen.writeNumberField(key, value.toDouble())
        is List<*> -> {
          gen.writeArrayFieldStart(key)
          for (item in value) {
            gen.writeString(item.toString())
          }
          gen.writeEndArray()
        }
      }
    }
    gen.writeEndObject()
  }
  gen.writeEndArray()
  
  gen.writeStringField("recommendation", impact.recommendation)
  gen.writeBooleanField("safe", impact.safe)
}
