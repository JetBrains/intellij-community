// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.impl.BazelModuleOutputProvider
import org.jetbrains.intellij.build.impl.JpsModuleOutputProvider
import org.jetbrains.intellij.build.impl.bazelOutputRoot
import org.jetbrains.intellij.build.productLayout.analysis.JsonFilter
import org.jetbrains.intellij.build.productLayout.analysis.ModuleSetMetadata
import org.jetbrains.intellij.build.productLayout.analysis.ProductCategory
import org.jetbrains.intellij.build.productLayout.analysis.ProductSpec
import org.jetbrains.jps.model.serialization.JpsMavenSettings
import org.jetbrains.jps.model.serialization.JpsSerializationManager
import java.nio.file.Path

/**
 * Determines product category based on module sets included in the content spec.
 * 
 * @param contentSpec Product's module content specification
 * @return ProductCategory based on which core module sets are used
 */
private fun determineProductCategory(contentSpec: ProductModulesContentSpec?): ProductCategory {
  if (contentSpec == null) return ProductCategory.BACKEND
  
  val moduleSetNames = contentSpec.moduleSets.map { it.moduleSet.name }
  return when {
    "ide.ultimate" in moduleSetNames -> ProductCategory.ULTIMATE
    "ide.common" in moduleSetNames -> ProductCategory.COMMUNITY
    else -> ProductCategory.BACKEND
  }
}

/**
 * Parses JSON argument from command line in the format `--json` or `--json='{"filter":"...","value":"..."}'`.
 * Returns null for full JSON output, or JsonFilter for filtered output.
 *
 * @param arg The command line argument (e.g., "--json" or "--json={...}")
 * @return JsonFilter if filter is specified, null for full JSON output
 */
fun parseJsonArgument(arg: String): JsonFilter? {
  if (arg.contains("=")) {
    val filterJson = arg.substringAfter("=")
    try {
      return Json.decodeFromString<JsonFilter>(filterJson)
    }
    catch (e: Exception) {
      System.err.println("Failed to parse JSON filter: $filterJson")
      System.err.println("Error: ${e.message}")
      return null
    }
  }
  else {
    // Full JSON output
    return null
  }
}

/**
 * Generic main runner for module set generation and analysis.
 * Supports two modes:
 * 1. JSON mode (--json): Outputs comprehensive analysis as JSON to stdout
 * 2. Default mode: Generates XML files for module sets and products
 *
 * @param args Command line arguments
 * @param communityModuleSets Module sets from community
 * @param ultimateModuleSets Module sets from ultimate (or empty for community-only)
 * @param testProducts Test product specifications (name to ProductModulesContentSpec pairs)
 * @param communitySourceFile Source file path for community module sets
 * @param ultimateSourceFile Source file path for ultimate module sets (or null for community-only)
 * @param projectRoot Project root path
 * @param generateXmlImpl Lambda to generate XML files (default mode implementation)
 */
fun runModuleSetMain(
  args: Array<String>,
  communityModuleSets: List<ModuleSet>,
  ultimateModuleSets: List<ModuleSet>,
  testProducts: List<Pair<String, ProductModulesContentSpec>> = emptyList(),
  communitySourceFile: String,
  ultimateSourceFile: String?,
  projectRoot: Path,
  generateXmlImpl: suspend (moduleOutputProvider: ModuleOutputProvider) -> Unit,
): Unit = runBlocking(Dispatchers.Default) {
  // Parse `--json` arg with optional filter
  val jsonArg = args.firstOrNull { it.startsWith("--json") }
  val moduleOutputProvider = createModuleOutputProvider(projectRoot)
  when {
    jsonArg != null -> {
      jsonResponse(
        communityModuleSets = communityModuleSets,
        communitySourceFile = communitySourceFile,
        ultimateSourceFile = ultimateSourceFile,
        ultimateModuleSets = ultimateModuleSets,
        projectRoot = projectRoot,
        testProducts = testProducts,
        jsonArg = jsonArg,
        moduleOutputProvider = moduleOutputProvider,
      )
    }
    else -> {
      // Default mode: Generate XML files
      generateXmlImpl(moduleOutputProvider)
    }
  }
}

private suspend fun jsonResponse(
  communityModuleSets: List<ModuleSet>,
  communitySourceFile: String,
  ultimateSourceFile: String?,
  ultimateModuleSets: List<ModuleSet>,
  projectRoot: Path,
  testProducts: List<Pair<String, ProductModulesContentSpec>>,
  jsonArg: String,
  moduleOutputProvider: ModuleOutputProvider,
) {
  // Prepare all module sets with metadata
  val communityModuleSetsWithMeta = communityModuleSets.map {
    ModuleSetMetadata(
      moduleSet = it,
      location = "community",
      sourceFile = communitySourceFile,
      directNestedSets = it.nestedSets.map { nested -> nested.name }
    )
  }
  val ultimateModuleSetsWithMeta = if (ultimateSourceFile == null) {
    emptyList()
  }
  else {
    ultimateModuleSets.map {
      ModuleSetMetadata(
        moduleSet = it,
        location = "ultimate",
        sourceFile = ultimateSourceFile,
        directNestedSets = it.nestedSets.map { nested -> nested.name }
      )
    }
  }
  val allModuleSets = communityModuleSetsWithMeta + ultimateModuleSetsWithMeta

  // Discover regular products and add passed test products
  val regularProducts = discoverAllProducts(projectRoot, moduleOutputProvider).asSequence().map {
    // For test products (properties = null), use "test-product" as source file
    val props = it.properties // Store in local val to enable smart cast
    val sourceFile = if (props == null) {
      "test-product"
    }
    else {
      // Use JPS-based lookup to find actual source file in module source roots
      findProductPropertiesSourceFile(
        buildModules = it.config.modules,
        productPropertiesClass = props.javaClass,
        moduleOutputProvider = moduleOutputProvider,
        projectRoot = projectRoot
      )
    }
    ProductSpec(
      name = it.name,
      className = it.config.className,
      sourceFile = sourceFile,
      pluginXmlPath = it.pluginXmlPath,
      contentSpec = it.spec,  // Pass full ProductModulesContentSpec for complete DSL serialization
      buildModules = it.config.modules,
      category = determineProductCategory(it.spec),
    )
  }
  val testProductSpecs = testProducts.asSequence().map { (name, spec) ->
    ProductSpec(
      name = name,
      className = "test-product",
      sourceFile = "test-product",
      pluginXmlPath = "ultimate/platform-ultimate/testResources/META-INF/${name}Plugin.xml",
      contentSpec = spec,
      buildModules = emptyList()
    )
  }

  streamModuleAnalysisJson(
    allModuleSets = allModuleSets,
    products = (regularProducts + testProductSpecs).toList(),
    projectRoot = projectRoot,
    filter = parseJsonArgument(jsonArg),
    moduleOutputProvider = moduleOutputProvider
  )
}

fun createModuleOutputProvider(projectRoot: Path): ModuleOutputProvider {
  val project = JpsSerializationManager.getInstance().loadProject(
    projectRoot.toString(),
    mapOf("MAVEN_REPOSITORY" to JpsMavenSettings.getMavenRepositoryPath()),
    false
  )
  val bazelOutputRoot = bazelOutputRoot
  return if (bazelOutputRoot != null) {
    BazelModuleOutputProvider(
      modules = project.modules,
      projectHome = projectRoot,
      bazelOutputRoot = bazelOutputRoot,
    )
  }
  else {
    JpsModuleOutputProvider(project)
  }
}
