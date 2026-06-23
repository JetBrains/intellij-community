// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.productLayout.json

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.PluginGraph
import com.intellij.platform.pluginGraph.TargetName
import kotlinx.serialization.Serializable
import org.jetbrains.intellij.build.productLayout.contentName
import org.jetbrains.intellij.build.productLayout.tooling.JsonFilter
import org.jetbrains.intellij.build.productLayout.tooling.ModuleSetMetadata
import org.jetbrains.intellij.build.productLayout.tooling.ParseResult
import org.jetbrains.intellij.build.productLayout.tooling.ProductSpec
import org.jetbrains.intellij.build.productLayout.tooling.analyzeProductSimilarity
import org.jetbrains.intellij.build.productLayout.tooling.analyzeProductUsage
import org.jetbrains.intellij.build.productLayout.tooling.detectModuleSetOverlap
import org.jetbrains.intellij.build.productLayout.tooling.suggestModuleSetUnification
import org.jetbrains.intellij.build.productLayout.traversal.analyzeEmbeddedDependencyClosure
import org.jetbrains.intellij.build.productLayout.traversal.collectDirectProductModuleNames
import org.jetbrains.intellij.build.productLayout.traversal.collectModuleSetDirectModuleNames
import org.jetbrains.intellij.build.productLayout.traversal.collectModuleSetDirectNestedNames
import org.jetbrains.intellij.build.productLayout.traversal.collectModuleSetModuleNames
import org.jetbrains.intellij.build.productLayout.traversal.collectProductModuleNames
import org.jetbrains.intellij.build.productLayout.traversal.collectProductModuleSetNames
import org.jetbrains.intellij.build.productLayout.traversal.findModuleSetInclusionChain
import org.jetbrains.intellij.build.productLayout.traversal.findModulePaths
import org.jetbrains.intellij.build.productLayout.traversal.getModuleDependencies
import org.jetbrains.intellij.build.productLayout.traversal.getModuleOwners
import org.jetbrains.intellij.build.productLayout.traversal.isModuleSetTransitivelyNested
import tools.jackson.core.JsonGenerator
import java.nio.file.Path

private fun JsonFilter.resultLimit(): Int = limit.coerceIn(1, 200)

private fun <T> List<T>.sample(filter: JsonFilter): List<T> {
  return if (filter.details) this else take(filter.resultLimit())
}

private inline fun <reified T> JsonGenerator.writeEncoded(fieldName: String, value: T) {
  writeName(fieldName)
  writeRawValue(kotlinxJson.encodeToString(value))
}

@Serializable
private data class CountedList<T>(
  @JvmField val count: Int,
  @JvmField val items: List<T>,
  @JvmField val truncated: Boolean,
)

private fun <T> counted(items: List<T>, filter: JsonFilter): CountedList<T> {
  val sample = items.sample(filter)
  return CountedList(count = items.size, items = sample, truncated = sample.size < items.size)
}

@Serializable
private data class NamedCount(
  @JvmField val name: String,
  @JvmField val count: Int,
)

internal fun writeSummaryQuery(
  gen: JsonGenerator,
  allModuleSets: List<ModuleSetMetadata>,
  products: List<ProductSpec>,
  pluginGraph: PluginGraph,
  filter: JsonFilter,
) {
  @Serializable
  data class ModuleSetSummary(
    @JvmField val total: Int,
    @JvmField val community: Int,
    @JvmField val ultimate: Int,
    @JvmField val topByModules: List<NamedCount>,
  )

  @Serializable
  data class ProductSummary(
    @JvmField val total: Int,
    @JvmField val withContentSpec: Int,
    @JvmField val topByModules: List<NamedCount>,
  )

  @Serializable
  data class Summary(
    @JvmField val moduleSets: ModuleSetSummary,
    @JvmField val products: ProductSummary,
    @JvmField val distinctContentModulesInModuleSets: Int,
  )

  val modules = HashSet<String>()
  val topModuleSets = ArrayList<NamedCount>()
  for ((moduleSet) in allModuleSets) {
    val count = collectModuleSetModuleNames(pluginGraph, moduleSet.name).size
    topModuleSets.add(NamedCount(moduleSet.name, count))
    for (moduleName in collectModuleSetModuleNames(pluginGraph, moduleSet.name)) {
      modules.add(moduleName.value)
    }
  }

  gen.writeEncoded("summary", Summary(
    moduleSets = ModuleSetSummary(
      total = allModuleSets.size,
      community = allModuleSets.count { it.location.name == "COMMUNITY" },
      ultimate = allModuleSets.count { it.location.name == "ULTIMATE" },
      topByModules = topModuleSets.sortedByDescending { it.count }.take(filter.resultLimit()),
    ),
    products = ProductSummary(
      total = products.size,
      withContentSpec = products.count { it.contentSpec != null },
      topByModules = products
        .map { NamedCount(it.name, collectProductModuleNames(pluginGraph, it.name).size) }
        .sortedByDescending { it.count }
        .take(filter.resultLimit()),
    ),
    distinctContentModulesInModuleSets = modules.size,
  ))
}

internal fun writeModuleInfoQuery(
  gen: JsonGenerator,
  allModuleSets: List<ModuleSetMetadata>,
  products: List<ProductSpec>,
  projectRoot: Path,
  pluginGraph: PluginGraph,
  filter: JsonFilter,
) {
  @Serializable
  data class ModuleInfo(
    @JvmField val module: String,
    @JvmField val moduleSets: CountedList<String>,
    @JvmField val products: CountedList<String>,
    @JvmField val owners: CountedList<String>,
    @JvmField val preferredOwner: String?,
    @JvmField val directDependencies: CountedList<String>,
    @JvmField val paths: CountedList<String>,
  )

  val moduleName = filter.module ?: filter.value
  if (moduleName == null) {
    gen.writeStringProperty("error", "module or value is required for moduleInfo")
    return
  }

  val paths = findModulePaths(ContentModuleName(moduleName), allModuleSets, products, projectRoot)
  val owners = getModuleOwners(ContentModuleName(moduleName), pluginGraph, includeTestSources = filter.includeTestSources)
  val dependencies = getModuleDependencies(TargetName(moduleName), pluginGraph, includeTestDependencies = filter.includeTestDependencies)
  val ownerNames = owners.owners.sortedWith(compareBy({ it.isTest }, { it.name.value })).map { it.name.value }

  gen.writeEncoded("moduleInfo", ModuleInfo(
    module = moduleName,
    moduleSets = counted(paths.moduleSets.sorted(), filter),
    products = counted(paths.products.sorted(), filter),
    owners = counted(ownerNames, filter),
    preferredOwner = ownerNames.firstOrNull(),
    directDependencies = counted(dependencies.dependencies.map { it.value }.sorted(), filter),
    paths = counted(paths.paths.map { it.path }.sorted(), filter),
  ))
}

internal fun writeModuleSetQuery(
  gen: JsonGenerator,
  allModuleSets: List<ModuleSetMetadata>,
  products: List<ProductSpec>,
  pluginGraph: PluginGraph,
  filter: JsonFilter,
) {
  @Serializable
  data class ModuleSetInfo(
    @JvmField val name: String,
    @JvmField val location: String,
    @JvmField val sourceFile: String,
    @JvmField val alias: String?,
    @JvmField val selfContained: Boolean,
    @JvmField val directModules: CountedList<String>,
    @JvmField val allModules: CountedList<String>,
    @JvmField val directNestedSets: CountedList<String>,
    @JvmField val includedByModuleSets: CountedList<String>,
    @JvmField val usedDirectlyByProducts: CountedList<String>,
    @JvmField val usedIndirectlyByProducts: CountedList<String>,
  )

  @Serializable
  data class ModuleSetSearchEntry(
    @JvmField val name: String,
    @JvmField val location: String,
    @JvmField val sourceFile: String,
    @JvmField val directModuleCount: Int,
    @JvmField val allModuleCount: Int,
    @JvmField val directNestedSetCount: Int,
  )

  @Serializable
  data class ModuleSetSearch(
    @JvmField val totalCount: Int,
    @JvmField val returnedCount: Int,
    @JvmField val results: List<ModuleSetSearchEntry>,
  )

  val moduleSetName = filter.moduleSet ?: filter.value
  if (moduleSetName != null) {
    val entry = allModuleSets.firstOrNull { it.moduleSet.name == moduleSetName }
    if (entry == null) {
      gen.writeStringProperty("error", "Module set '$moduleSetName' not found")
      return
    }

    val usage = analyzeProductUsage(moduleSetName, products, pluginGraph)
    val includedBy = allModuleSets
      .filter { it.moduleSet.name != moduleSetName && isModuleSetTransitivelyNested(pluginGraph, it.moduleSet.name, moduleSetName) }
      .map { it.moduleSet.name }
      .sorted()

    gen.writeEncoded("moduleSetQuery", ModuleSetInfo(
      name = entry.moduleSet.name,
      location = entry.location.name,
      sourceFile = entry.sourceFile,
      alias = entry.moduleSet.alias?.value,
      selfContained = entry.moduleSet.selfContained,
      directModules = counted(collectModuleSetDirectModuleNames(pluginGraph, moduleSetName).map { it.value }.sorted(), filter),
      allModules = counted(collectModuleSetModuleNames(pluginGraph, moduleSetName).map { it.value }.sorted(), filter),
      directNestedSets = counted(collectModuleSetDirectNestedNames(pluginGraph, moduleSetName).toList().sorted(), filter),
      includedByModuleSets = counted(includedBy, filter),
      usedDirectlyByProducts = counted(usage.directUsage.map { it.product }.sorted(), filter),
      usedIndirectlyByProducts = counted(usage.indirectUsage.map { it.product }.sorted(), filter),
    ))
    return
  }

  val nameFilter = filter.name
  val locationFilter = filter.location?.uppercase()
  val results = allModuleSets.asSequence()
    .filter { nameFilter == null || it.moduleSet.name.contains(nameFilter, ignoreCase = true) }
    .filter { locationFilter == null || it.location.name == locationFilter }
    .map {
      ModuleSetSearchEntry(
        name = it.moduleSet.name,
        location = it.location.name,
        sourceFile = it.sourceFile,
        directModuleCount = collectModuleSetDirectModuleNames(pluginGraph, it.moduleSet.name).size,
        allModuleCount = collectModuleSetModuleNames(pluginGraph, it.moduleSet.name).size,
        directNestedSetCount = collectModuleSetDirectNestedNames(pluginGraph, it.moduleSet.name).size,
      )
    }
    .filter { it.allModuleCount >= filter.minModuleCount }
    .sortedBy { it.name }
    .toList()

  gen.writeEncoded("moduleSetQuery", ModuleSetSearch(
    totalCount = results.size,
    returnedCount = minOf(results.size, filter.resultLimit()),
    results = results.take(filter.resultLimit()),
  ))
}

internal fun writeProductQuery(
  gen: JsonGenerator,
  products: List<ProductSpec>,
  pluginGraph: PluginGraph,
  filter: JsonFilter,
) {
  @Serializable
  data class ProductInfo(
    @JvmField val name: String,
    @JvmField val sourceFile: String,
    @JvmField val pluginXmlPath: String?,
    @JvmField val category: String,
    @JvmField val moduleSets: CountedList<String>,
    @JvmField val directModules: CountedList<String>,
    @JvmField val allModules: CountedList<String>,
    @JvmField val aliases: CountedList<String>,
  )

  @Serializable
  data class ProductSearchEntry(
    @JvmField val name: String,
    @JvmField val sourceFile: String,
    @JvmField val moduleSetCount: Int,
    @JvmField val directModuleCount: Int,
    @JvmField val allModuleCount: Int,
    @JvmField val moduleSetUsage: String? = null,
    @JvmField val inclusionChain: List<String>? = null,
  )

  @Serializable
  data class ProductSearch(
    @JvmField val totalCount: Int,
    @JvmField val returnedCount: Int,
    @JvmField val results: List<ProductSearchEntry>,
  )

  val productName = filter.product ?: filter.value
  if (productName != null) {
    val product = products.firstOrNull { it.name == productName }
    if (product == null) {
      gen.writeStringProperty("error", "Product '$productName' not found")
      return
    }

    gen.writeEncoded("productQuery", ProductInfo(
      name = product.name,
      sourceFile = product.sourceFile,
      pluginXmlPath = product.pluginXmlPath,
      category = product.category.name,
      moduleSets = counted(collectProductModuleSetNames(pluginGraph, product.name).toList().sorted(), filter),
      directModules = counted(collectDirectProductModuleNames(pluginGraph, product.name).map { it.value }.sorted(), filter),
      allModules = counted(collectProductModuleNames(pluginGraph, product.name).map { it.value }.sorted(), filter),
      aliases = counted(product.aliases.sorted(), filter),
    ))
    return
  }

  val nameFilter = filter.name
  val usesModuleSet = filter.usesModuleSet
  val results = products.asSequence()
    .filter { it.contentSpec != null }
    .filter { nameFilter == null || it.name.contains(nameFilter, ignoreCase = true) }
    .mapNotNull { product ->
      val topLevelSets = collectProductModuleSetNames(pluginGraph, product.name).toList().sorted()
      var usage: String? = null
      var chain: List<String>? = null
      if (usesModuleSet != null) {
        if (usesModuleSet in topLevelSets) {
          usage = "direct"
          chain = listOf(product.name, usesModuleSet)
        }
        else {
          for (topLevelSet in topLevelSets) {
            val setChain = findModuleSetInclusionChain(pluginGraph, topLevelSet, usesModuleSet)
            if (setChain != null) {
              usage = "indirect"
              chain = listOf(product.name) + setChain
              break
            }
          }
        }
        if (usage == null) return@mapNotNull null
      }
      val allModuleCount = collectProductModuleNames(pluginGraph, product.name).size
      if (allModuleCount < filter.minModuleCount) return@mapNotNull null
      ProductSearchEntry(
        name = product.name,
        sourceFile = product.sourceFile,
        moduleSetCount = topLevelSets.size,
        directModuleCount = collectDirectProductModuleNames(pluginGraph, product.name).size,
        allModuleCount = allModuleCount,
        moduleSetUsage = usage,
        inclusionChain = chain,
      )
    }
    .sortedBy { it.name }
    .toList()

  gen.writeEncoded("productQuery", ProductSearch(
    totalCount = results.size,
    returnedCount = minOf(results.size, filter.resultLimit()),
    results = results.take(filter.resultLimit()),
  ))
}

internal fun writeProductCompareQuery(
  gen: JsonGenerator,
  products: List<ProductSpec>,
  pluginGraph: PluginGraph,
  filter: JsonFilter,
) {
  @Serializable
  data class Difference(
    @JvmField val count: Int,
    @JvmField val sample: List<String>,
  )

  @Serializable
  data class ProductCompare(
    @JvmField val product1: String,
    @JvmField val product2: String,
    @JvmField val sharedModuleSets: Difference,
    @JvmField val uniqueModuleSetsToProduct1: Difference,
    @JvmField val uniqueModuleSetsToProduct2: Difference,
    @JvmField val sharedModules: Difference,
    @JvmField val uniqueModulesToProduct1: Difference,
    @JvmField val uniqueModulesToProduct2: Difference,
  )

  val product1 = filter.product ?: filter.value
  val product2 = filter.product2
  if (product1 == null || product2 == null) {
    gen.writeStringProperty("error", "product/value and product2 are required for productCompare")
    return
  }
  if (products.none { it.name == product1 }) {
    gen.writeStringProperty("error", "Product '$product1' not found")
    return
  }
  if (products.none { it.name == product2 }) {
    gen.writeStringProperty("error", "Product '$product2' not found")
    return
  }

  fun difference(items: Collection<String>): Difference {
    val sorted = items.sorted()
    return Difference(count = sorted.size, sample = sorted.sample(filter))
  }

  val sets1 = collectProductModuleSetNames(pluginGraph, product1).mapTo(HashSet()) { it }
  val sets2 = collectProductModuleSetNames(pluginGraph, product2).mapTo(HashSet()) { it }
  val modules1 = collectProductModuleNames(pluginGraph, product1).mapTo(HashSet()) { it.value }
  val modules2 = collectProductModuleNames(pluginGraph, product2).mapTo(HashSet()) { it.value }

  gen.writeEncoded("productCompare", ProductCompare(
    product1 = product1,
    product2 = product2,
    sharedModuleSets = difference(sets1.intersect(sets2)),
    uniqueModuleSetsToProduct1 = difference(sets1.minus(sets2)),
    uniqueModuleSetsToProduct2 = difference(sets2.minus(sets1)),
    sharedModules = difference(modules1.intersect(modules2)),
    uniqueModulesToProduct1 = difference(modules1.minus(modules2)),
    uniqueModulesToProduct2 = difference(modules2.minus(modules1)),
  ))
}

internal fun writeProductTracePathQuery(
  gen: JsonGenerator,
  products: List<ProductSpec>,
  pluginGraph: PluginGraph,
  filter: JsonFilter,
) {
  @Serializable
  data class ProductTracePath(
    @JvmField val product: String,
    @JvmField val moduleSet: String?,
    @JvmField val topLevelModuleSets: List<String>,
    @JvmField val paths: List<List<String>>,
  )

  val productName = filter.product ?: filter.value
  if (productName == null) {
    gen.writeStringProperty("error", "product or value is required for productTracePath")
    return
  }
  if (products.none { it.name == productName }) {
    gen.writeStringProperty("error", "Product '$productName' not found")
    return
  }

  val topLevelSets = collectProductModuleSetNames(pluginGraph, productName).toList().sorted()
  val requestedSet = filter.moduleSet
  val paths = if (requestedSet == null) {
    topLevelSets.map { listOf(productName, it) }
  }
  else {
    topLevelSets.mapNotNull { topLevelSet ->
      findModuleSetInclusionChain(pluginGraph, topLevelSet, requestedSet)?.let { listOf(productName) + it }
    }
  }

  if (requestedSet != null && paths.isEmpty()) {
    gen.writeStringProperty("error", "Module set '$requestedSet' is not used by product '$productName'")
    return
  }

  gen.writeEncoded("productTracePath", ProductTracePath(
    product = productName,
    moduleSet = requestedSet,
    topLevelModuleSets = topLevelSets,
    paths = paths.sample(filter),
  ))
}

internal suspend fun writeParameterizedModuleSetOverlap(
  gen: JsonGenerator,
  allModuleSets: List<ModuleSetMetadata>,
  pluginGraph: PluginGraph,
  filter: JsonFilter,
) {
  val overlaps = detectModuleSetOverlap(allModuleSets, pluginGraph, filter.minOverlapPercent)
  gen.writeName("moduleSetOverlap")
  writeModuleSetOverlapAnalysis(gen, overlaps.take(filter.resultLimit()), filter.minOverlapPercent)
}

internal suspend fun writeParameterizedProductSimilarity(
  gen: JsonGenerator,
  products: List<ProductSpec>,
  pluginGraph: PluginGraph,
  filter: JsonFilter,
) {
  val pairs = analyzeProductSimilarity(products, pluginGraph, filter.threshold)
  gen.writeName("productSimilarity")
  writeProductSimilarityAnalysis(gen, pairs.take(filter.resultLimit()), filter.threshold)
}

internal suspend fun writeParameterizedUnificationSuggestions(
  gen: JsonGenerator,
  allModuleSets: List<ModuleSetMetadata>,
  products: List<ProductSpec>,
  pluginGraph: PluginGraph,
  filter: JsonFilter,
) {
  val overlaps = detectModuleSetOverlap(allModuleSets, pluginGraph, filter.minOverlapPercent)
  val similarityPairs = analyzeProductSimilarity(products, pluginGraph, filter.threshold)
  val suggestions = suggestModuleSetUnification(
    allModuleSets = allModuleSets,
    products = products,
    overlaps = overlaps,
    similarityPairs = similarityPairs,
    pluginGraph = pluginGraph,
    maxSuggestions = filter.resultLimit(),
    strategy = filter.strategy ?: "all",
  )
  gen.writeName("unificationSuggestions")
  writeUnificationSuggestions(gen, suggestions)
}

internal fun writeValidationQuery(
  gen: JsonGenerator,
  allModuleSets: List<ModuleSetMetadata>,
  products: List<ProductSpec>,
  projectRoot: Path,
  pluginGraph: PluginGraph,
  filter: JsonFilter,
) {
  val check = filter.check ?: filter.value ?: "community_products"
  val moduleLocationsResult = parseModulesXml(projectRoot)
  val moduleLocations = when (moduleLocationsResult) {
    is ParseResult.Success -> moduleLocationsResult.value
    is ParseResult.Failure -> moduleLocationsResult.partial ?: emptyMap()
  }

  gen.writeObjectPropertyStart("validation")
  gen.writeStringProperty("check", check)
  when (check) {
    "community_products" -> writeCommunityProductViolations(gen, validateCommunityProducts(products, allModuleSets, moduleLocations, projectRoot, pluginGraph))
    "module_set_locations" -> writeModuleSetLocationViolations(gen, validateModuleSetLocations(allModuleSets, moduleLocations, projectRoot, pluginGraph))
    "loading_inconsistencies" -> writeModuleLoadingQueryBody(gen, allModuleSets, products, filter.copy(loading = null, details = true))
    "embedded_dependency_closure" -> {
      gen.writeName("result")
      writeEmbeddedDependencyClosureResult(gen, analyzeEmbeddedDependencyClosure(
        graph = pluginGraph,
        productName = filter.product ?: filter.value,
        moduleSetName = filter.moduleSet,
        moduleName = filter.module,
        pluginSourceOnly = filter.pluginSourceOnly,
      ))
    }
    else -> gen.writeStringProperty("error", "Unknown validation check: $check")
  }
  gen.writeEndObject()
}

internal fun writeModuleLoadingQuery(
  gen: JsonGenerator,
  allModuleSets: List<ModuleSetMetadata>,
  products: List<ProductSpec>,
  filter: JsonFilter,
) {
  gen.writeObjectPropertyStart("moduleLoading")
  writeModuleLoadingQueryBody(gen, allModuleSets, products, filter)
  gen.writeEndObject()
}

private fun writeModuleLoadingQueryBody(
  gen: JsonGenerator,
  allModuleSets: List<ModuleSetMetadata>,
  products: List<ProductSpec>,
  filter: JsonFilter,
) {
  @Serializable
  data class LoadingEntry(
    @JvmField val module: String,
    @JvmField val loading: String,
    @JvmField val context: String,
    @JvmField val owner: String,
  )

  @Serializable
  data class LoadingSummary(
    @JvmField val counts: Map<String, Int>,
    @JvmField val inconsistencies: Map<String, List<String>>,
    @JvmField val entries: CountedList<LoadingEntry>,
  )

  val loadingFilter = filter.loading?.uppercase()
  val entries = ArrayList<LoadingEntry>()
  for ((moduleSet) in allModuleSets) {
    for (module in moduleSet.modules) {
      entries.add(LoadingEntry(module.contentName().value, module.loading.name, "moduleSet", moduleSet.name))
    }
  }
  for ((productName, _, _, _, contentSpec) in products) {
    contentSpec ?: continue
    for (module in contentSpec.additionalModules) {
      entries.add(LoadingEntry(module.contentName().value, module.loading.name, "product", productName))
    }
  }

  val filtered = entries
    .filter { loadingFilter == null || it.loading == loadingFilter }
    .sortedWith(compareBy({ it.loading }, { it.module }, { it.owner }))
  val inconsistencies = entries
    .groupBy { it.module }
    .mapValues { it.value.mapTo(LinkedHashSet()) { entry -> entry.loading }.sorted() }
    .filter { it.value.size > 1 }
    .toSortedMap()

  gen.writeEncoded("result", LoadingSummary(
    counts = entries.groupingBy { it.loading }.eachCount().toSortedMap(),
    inconsistencies = inconsistencies,
    entries = counted(filtered, filter),
  ))
}

internal fun writeDeprecatedIncludesQuery(
  gen: JsonGenerator,
  products: List<ProductSpec>,
  filter: JsonFilter,
) {
  @Serializable
  data class DeprecatedIncludeEntry(
    @JvmField val product: String,
    @JvmField val module: String,
    @JvmField val resourcePath: String,
    @JvmField val optional: Boolean,
  )

  val productFilter = filter.product ?: filter.value
  val includes = ArrayList<DeprecatedIncludeEntry>()
  for ((productName, _, _, _, contentSpec) in products) {
    if (productFilter != null && productName != productFilter) continue
    contentSpec ?: continue
    for ((contentModuleName, resourcePath, optional) in contentSpec.deprecatedXmlIncludes) {
      includes.add(DeprecatedIncludeEntry(productName, contentModuleName.value, resourcePath, optional))
    }
  }
  gen.writeEncoded("deprecatedIncludes", counted(includes.sortedWith(compareBy({ it.product }, { it.module }, { it.resourcePath })), filter))
}

internal fun writeRedundantModuleSetRefsQuery(
  gen: JsonGenerator,
  products: List<ProductSpec>,
  pluginGraph: PluginGraph,
  filter: JsonFilter,
) {
  @Serializable
  data class RedundantRef(
    @JvmField val product: String,
    @JvmField val redundantModuleSet: String,
    @JvmField val alreadyInModuleSet: String,
    @JvmField val recommendation: String,
  )

  val productFilter = filter.product ?: filter.value
  val redundancies = ArrayList<RedundantRef>()
  for ((productName) in products) {
    if (productFilter != null && productName != productFilter) continue
    val topLevelSets = collectProductModuleSetNames(pluginGraph, productName).toList().sorted()
    for (setName in topLevelSets) {
      for (otherSetName in topLevelSets) {
        if (setName != otherSetName && isModuleSetTransitivelyNested(pluginGraph, otherSetName, setName)) {
          redundancies.add(RedundantRef(
            product = productName,
            redundantModuleSet = setName,
            alreadyInModuleSet = otherSetName,
            recommendation = "Remove direct moduleSet($setName()) reference; it is already nested in '$otherSetName'.",
          ))
        }
      }
    }
  }
  gen.writeEncoded("redundantModuleSetRefs", counted(redundancies, filter))
}

internal fun writeSuggestModuleSetsForModulesQuery(
  gen: JsonGenerator,
  allModuleSets: List<ModuleSetMetadata>,
  products: List<ProductSpec>,
  pluginGraph: PluginGraph,
  filter: JsonFilter,
) {
  @Serializable
  data class ModuleUsage(
    @JvmField val module: String,
    @JvmField val moduleSets: List<String>,
    @JvmField val products: List<String>,
  )

  @Serializable
  data class Suggestion(
    @JvmField val modules: List<String>,
    @JvmField val perModule: List<ModuleUsage>,
    @JvmField val moduleSetsContainingAll: List<String>,
    @JvmField val productsUsingAll: List<String>,
  )

  val moduleNames = filter.modules.ifEmpty { filter.module?.let { listOf(it) } ?: emptyList() }
  if (moduleNames.isEmpty()) {
    gen.writeStringProperty("error", "modules or module is required for suggestModuleSetsForModules")
    return
  }

  val perModule = moduleNames.map { moduleName ->
    val contentModuleName = ContentModuleName(moduleName)
    val moduleSets = allModuleSets
      .filter { collectModuleSetModuleNames(pluginGraph, it.moduleSet.name).contains(contentModuleName) }
      .map { it.moduleSet.name }
      .sorted()
    val productNames = products
      .filter { collectProductModuleNames(pluginGraph, it.name).contains(contentModuleName) }
      .map { it.name }
      .sorted()
    ModuleUsage(moduleName, moduleSets, productNames)
  }

  fun intersect(values: List<List<String>>): List<String> {
    if (values.isEmpty()) return emptyList()
    val result = values.first().toMutableSet()
    for (value in values.drop(1)) {
      result.retainAll(value.toSet())
    }
    return result.sorted()
  }

  gen.writeEncoded("suggestModuleSetsForModules", Suggestion(
    modules = moduleNames,
    perModule = perModule,
    moduleSetsContainingAll = intersect(perModule.map { it.moduleSets }).sample(filter),
    productsUsingAll = intersect(perModule.map { it.products }).sample(filter),
  ))
}
