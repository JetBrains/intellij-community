// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment", "GrazieInspection", "GrazieStyle")

package org.jetbrains.intellij.build.productLayout.discovery

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.TargetName
import com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRuleValue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.productLayout.ProductModulesContentSpec
import org.jetbrains.intellij.build.productLayout.generateProductXml
import org.jetbrains.intellij.build.productLayout.model.error.FileDiff
import org.jetbrains.intellij.build.productLayout.model.error.ValidationError
import org.jetbrains.intellij.build.productLayout.pipeline.GenerationPipeline
import org.jetbrains.intellij.build.productLayout.stats.GenerationStats
import org.jetbrains.intellij.build.productLayout.stats.ProductGenerationResult
import org.jetbrains.intellij.build.productLayout.util.FileUpdateStrategy
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
  @JvmField val nonBundledPlugins: Map<String, Set<TargetName>> = emptyMap(),
  /**
   * Set of plugin module names that are known to exist but not associated with any specific product.
   * Used for validation purposes only (to recognize these plugins as valid dependency targets).
   */
  @JvmField val knownPlugins: Set<TargetName> = emptySet(),
  /**
   * Map of product name to set of test plugin names for that product.
   * Test plugins have plugin.xml in test resources and provide test framework modules.
   * Used for: (1) content extraction with onlyProductionSources=false, (2) test dep validation.
   */
  @JvmField val testPluginsByProduct: Map<String, Set<TargetName>> = emptyMap(),

  /**
   * When true, scan module sources for test plugin descriptors and plugin-content.yaml
   * to enrich the PluginGraph in analysis-only flows.
   */
  @JvmField val includeTestPluginDescriptorsFromSources: Boolean = false,

  /** Xi:include paths to skip during plugin content extraction (e.g., paths from external libraries like Kotlin compiler) */
  @JvmField val skipXIncludePaths: Set<String> = emptySet(),
  /** Returns prefix for xi:include filtering or null to disable filtering for this module */
  @JvmField val xIncludePrefixFilter: (moduleName: String) -> String? = { null },
  /**
   * Modules that indicate a plugin is a test plugin when declared as content.
   * Plugins declaring any of these as `<content>` are excluded from production validation
   * because they won't be present at runtime.
   */
  @JvmField val testFrameworkContentModules: Set<ContentModuleName> = emptySet(),
  /**
   * Library names that are considered testing libraries.
   * Modules in product distributions should have these libraries in 'test' scope only.
   * If a module has these libraries in production scope, a diff will be generated to fix it.
   */
  @JvmField val testingLibraries: Set<String> = emptySet(),
  /**
   * Modules allowed having specific testing libraries in production scope.
   * Maps module name to the set of testing library names it's allowed to have.
   * More precise than a blanket allowlist - each module can only have the specific libraries it needs.
   */
  @JvmField val testLibraryAllowedInModule: Map<ContentModuleName, Set<String>> = emptyMap(),
  /**
   * Map of plugin module name to set of allowed missing dependencies.
   * Used to suppress validation errors for plugin dependencies that are intentionally missing
   * (e.g., bundled via withProjectLibrary or other mechanisms not visible to the validator).
   */
  @JvmField val pluginAllowedMissingDependencies: Map<ContentModuleName, Set<ContentModuleName>> = emptyMap(),

  /**
   * Filter to control which library modules should replace library references in .iml files.
   * When a library is exported by a library module (e.g., Guava exported by intellij.libraries.guava),
   * this filter determines whether the library reference should be replaced with a module reference.
   *
   * The filter receives the library module name (e.g., "intellij.libraries.guava").
   * @return true if the library should be replaced with a module dependency, false to keep the library reference
   */
  @JvmField val libraryModuleFilter: (libraryModuleName: String) -> Boolean = { true },

  /**
   * Map from project library name to the library module that exports it.
   * Built from JPS library modules (e.g., intellij.libraries.*) and used to map project
   * library dependencies to module targets.
   */
  @JvmField val projectLibraryToModuleMap: Map<String, String> = emptyMap(),

  /**
   * Path to the suppressions.json file.
   * If null, suppression config is not loaded/saved.
   * Should be set by the caller (e.g., ultimateGenerator.kt).
   */
  @JvmField val suppressionConfigPath: Path? = null,

  /**
   * Filter for validation rules. When non-null, only validation rules with matching names run.
   * Generation generators always run regardless of this filter.
   * - `null` = run all validation rules (default)
   * - `emptySet()` = skip all validation rules
   * - `setOf("productModuleSetValidation")` = run only ProductModuleSetValidationRule
   */
  @JvmField val validationFilter: Set<String>? = null,
  /**
   * Loading mode for content modules auto-added to DSL test plugins during dependency traversal.
   * Default is OPTIONAL to avoid forcing required loading unless explicitly configured.
   */
  @JvmField val dslTestPluginAutoAddLoadingMode: ModuleLoadingRuleValue = ModuleLoadingRuleValue.OPTIONAL,
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
  strategy: FileUpdateStrategy,
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
 *
 * Delegates to [GenerationPipeline] for orchestrated generation through 5 stages:
 * 1. **DISCOVER** - Scan DSL definitions for module sets and products
 * 2. **BUILD_MODEL** - Create caches and compute shared values
 * 3. **GENERATE** - Run registered generators in parallel
 * 4. **AGGREGATE** - Collect errors, diffs, and stats
 * 5. **OUTPUT** - Commit changes or return diffs
 *
 * @param config Configuration specifying module set sources, discovered products, test products, and other parameters
 * @param commitChanges If true, commits writes to disk when validation passes. If false, returns diffs without writing.
 * @return Result containing validation errors and diffs
 */
suspend fun generateAllModuleSetsWithProducts(
  config: ModuleSetGenerationConfig,
  commitChanges: Boolean = true,
  updateSuppressions: Boolean = false,
): GenerationResult {
  return GenerationPipeline.default().execute(config = config, commitChanges = commitChanges, updateSuppressions = updateSuppressions, validationFilter = config.validationFilter)
}
