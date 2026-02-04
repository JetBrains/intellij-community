// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout

import com.intellij.platform.pluginGraph.PluginGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.impl.BazelModuleOutputProvider
import org.jetbrains.intellij.build.impl.JpsModuleOutputProvider
import org.jetbrains.intellij.build.impl.bazelOutputRoot
import org.jetbrains.intellij.build.productLayout.discovery.GenerationResult
import org.jetbrains.intellij.build.productLayout.discovery.ModuleSetGenerationConfig
import org.jetbrains.intellij.build.productLayout.discovery.findProductPropertiesSourceFile
import org.jetbrains.intellij.build.productLayout.json.buildPluginGraphForJson
import org.jetbrains.intellij.build.productLayout.json.streamModuleAnalysisJson
import org.jetbrains.intellij.build.productLayout.stats.printGenerationSummary
import org.jetbrains.intellij.build.productLayout.tooling.JsonFilter
import org.jetbrains.intellij.build.productLayout.tooling.ModuleLocation
import org.jetbrains.intellij.build.productLayout.tooling.ModuleSetMetadata
import org.jetbrains.intellij.build.productLayout.tooling.ProductCategory
import org.jetbrains.intellij.build.productLayout.tooling.ProductSpec
import org.jetbrains.intellij.build.telemetry.withoutTracer
import org.jetbrains.jps.model.serialization.JpsMavenSettings
import org.jetbrains.jps.model.serialization.JpsSerializationManager
import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * Determines product category based on module sets included in the content spec.
 * 
 * @param contentSpec Product's module content specification
 * @return ProductCategory, based on which core module sets are used
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
 * Generic main runner for module set generation and analysis.
 * Supports multiple modes:
 * 1. JSON mode (--json): Outputs comprehensive analysis as JSON to stdout
 * 2. Update suppressions mode (--update-suppressions): Only updates suppressions.json, no XML changes
 * 3. Default mode: Generates XML files for module sets and products (but NOT suppressions.json)
 *
 * CLI flags:
 * - `--json[=filter]`: Output analysis as JSON instead of generating files
 * - `--update-suppressions`: Only update suppressions.json (no XML changes)
 * - `--validation=<ids>`: Run only specified validation rules (comma-separated).
 *   Use `--validation=none` to skip all validation. Generation generators always run.
 *
 * @param args Command line arguments
 * @param communityModuleSets Module sets from community
 * @param ultimateModuleSets Module sets from ultimate (or empty for community-only)
 * @param testProducts Test product specifications (name to ProductModulesContentSpec pairs)
 * @param communitySourceFile Source file path for community module sets
 * @param ultimateSourceFile Source file path for ultimate module sets (or null for community-only)
 * @param projectRoot Project root path
 * @param generateXmlImpl Lambda to generate XML files, returns generation result with errors and diffs
 * @param graphConfigProvider Optional provider for building ModuleSetGenerationConfig when JSON analysis needs PluginGraph
 */
suspend fun runModuleSetMain(
  args: Array<String>,
  communityModuleSets: List<ModuleSet>,
  ultimateModuleSets: List<ModuleSet>,
  testProducts: List<Pair<String, ProductModulesContentSpec>> = emptyList(),
  communitySourceFile: String,
  ultimateSourceFile: String?,
  projectRoot: Path,
  generateXmlImpl: suspend (outputProvider: ModuleOutputProvider, options: GeneratorRunOptions) -> GenerationResult,
  graphConfigProvider: (suspend (outputProvider: ModuleOutputProvider, options: GeneratorRunOptions) -> ModuleSetGenerationConfig)? = null,
) {
  withoutTracer {
    val options = parseGeneratorOptions(args)
    setProductDslLogFilter(options.logFilter)
    coroutineScope {
      val outputProvider = createModuleOutputProvider(projectRoot = projectRoot, scope = this)
      if (options.jsonFilter != null) {
        val filter = parseJsonArgument(options.jsonFilter)
        val pluginGraph = graphConfigProvider?.let { buildPluginGraphForJson(it(outputProvider, options)) }
          ?: error("PluginGraph is required for --json output; graphConfigProvider was not supplied")
        jsonResponse(
          communityModuleSets = communityModuleSets,
          communitySourceFile = communitySourceFile,
          ultimateSourceFile = ultimateSourceFile,
          ultimateModuleSets = ultimateModuleSets,
          projectRoot = projectRoot,
          testProducts = testProducts,
          filter = filter,
          outputProvider = outputProvider,
          pluginGraph = pluginGraph,
        )
      }
      else if (options.updateSuppressions) {
        // Update suppressions mode: only update suppressions.json, no XML changes
        val result = generateXmlImpl(outputProvider, options)
        println("Suppressions config updated.")
        printGenerationSummary(result.stats, result.errors)
      }
      else {
        // Default mode: Generate XML files but NOT suppressions.json
        val result = generateXmlImpl(outputProvider, options)
        printGenerationSummary(result.stats, result.errors)
        if (result.errors.isNotEmpty()) {
          exitProcess(1)
        }
      }
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
  filter: JsonFilter?,
  outputProvider: ModuleOutputProvider,
  pluginGraph: PluginGraph,
) {
  // Prepare all module sets with metadata
  val communityModuleSetsWithMeta = communityModuleSets.map {
    ModuleSetMetadata(
      moduleSet = it,
      location = ModuleLocation.COMMUNITY,
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
        location = ModuleLocation.ULTIMATE,
        sourceFile = ultimateSourceFile,
        directNestedSets = it.nestedSets.map { nested -> nested.name }
      )
    }
  }
  val allModuleSets = communityModuleSetsWithMeta + ultimateModuleSetsWithMeta

  // Discover regular products and add passed test products
  val regularProducts = discoverAllProducts(projectRoot, outputProvider).asSequence().map {
    // For test products (properties = null), use "test-product" as source file
    val props = it.properties // Store in local val to enable smart cast
    val sourceFile = if (props == null) {
      "test-product"
    }
    else {
      // Use JPS-based lookup to find actual source file in module source roots
      findProductPropertiesSourceFile(buildModules = it.config.modules, productPropertiesClass = props.javaClass, outputProvider = outputProvider, projectRoot = projectRoot)
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
    filter = filter,
    pluginGraph = pluginGraph,
  )
}

/**
 * Parses JSON argument from command line in the format `--json` or `--json='{"filter":"...","value":"..."}'`.
 * Returns null for full JSON output, or JsonFilter for filtered output.
 *
 * @param arg The command line argument (e.g., "--json" or "--json={...}")
 * @return JsonFilter if filter is specified, null for full JSON output
 */
private fun parseJsonArgument(arg: String): JsonFilter? {
  if (arg.contains('=')) {
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

private fun createModuleOutputProvider(projectRoot: Path, scope: CoroutineScope): ModuleOutputProvider {
  val useTestCompilationOutput = true
  val project = JpsSerializationManager.getInstance().loadProject(
    projectRoot.toString(),
    mapOf("MAVEN_REPOSITORY" to JpsMavenSettings.getMavenRepositoryPath()),
    false
  )
  val bazelOutputRoot = bazelOutputRoot ?: return JpsModuleOutputProvider(project, useTestCompilationOutput = useTestCompilationOutput)
  return BazelModuleOutputProvider(
    modules = project.modules,
    projectHome = projectRoot,
    bazelOutputRoot = bazelOutputRoot,
    scope = scope,
    useTestCompilationOutput = useTestCompilationOutput,
  )
}
