// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment", "GrazieInspection", "GrazieStyle")

package org.jetbrains.intellij.build.productLayout.validator

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.DependencyClassification
import com.intellij.platform.pluginGraph.GraphScope
import com.intellij.platform.pluginGraph.PluginGraph
import com.intellij.platform.pluginGraph.TargetName
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.productLayout.LIB_MODULE_PREFIX
import org.jetbrains.intellij.build.productLayout.config.SuppressionConfig
import org.jetbrains.intellij.build.productLayout.dependency.ModuleDescriptorCache
import org.jetbrains.intellij.build.productLayout.discovery.DiscoveredProduct
import org.jetbrains.intellij.build.productLayout.discovery.findProductPropertiesSourceFile
import org.jetbrains.intellij.build.productLayout.model.ModuleSourceInfo
import org.jetbrains.intellij.build.productLayout.model.error.PluginDependencyError
import org.jetbrains.intellij.build.productLayout.model.error.ProposedPatch
import org.jetbrains.intellij.build.productLayout.model.error.ValidationError
import org.jetbrains.intellij.build.productLayout.model.getModuleSourceInfo
import org.jetbrains.intellij.build.productLayout.pipeline.ComputeContext
import org.jetbrains.intellij.build.productLayout.pipeline.DataSlot
import org.jetbrains.intellij.build.productLayout.pipeline.NodeIds
import org.jetbrains.intellij.build.productLayout.pipeline.PipelineNode
import org.jetbrains.intellij.build.productLayout.pipeline.Slots
import org.jetbrains.intellij.build.productLayout.stats.DependencyFileResult
import org.jetbrains.intellij.build.productLayout.validator.rule.ResolutionQuery
import org.jetbrains.intellij.build.productLayout.validator.rule.collectDependenciesByLoadingMode
import org.jetbrains.intellij.build.productLayout.validator.rule.createResolutionQuery
import org.jetbrains.intellij.build.productLayout.validator.rule.existsAnywhere
import org.jetbrains.intellij.build.productLayout.validator.rule.existsInNonTestSource
import org.jetbrains.intellij.build.productLayout.validator.rule.forDslTestPlugin
import org.jetbrains.intellij.build.productLayout.validator.rule.forProductionPlugin
import org.jetbrains.intellij.build.productLayout.validator.rule.forTestPlugin
import java.nio.file.Files
import java.nio.file.Path

/**
 * Plugin content dependency validation (plugin-level).
 *
 * Purpose: Ensure plugin content dependencies resolve and check filtered deps.
 * Inputs: `Slots.CONTENT_MODULE`, plugin graph, suppression config and allowlists, plugin content cache.
 * Output: `PluginDependencyError`.
 * Auto-fix: Updates non-DSL plugin.xml loading for structural violations.
 *
 * Glossary: docs/validators/README.md.
 * Spec: docs/validators/plugin-content-dependency.md.
 */
internal object PluginContentDependencyValidator : PipelineNode {
  override val id get() = NodeIds.PLUGIN_VALIDATION

  // Requires CONTENT_MODULE because ContentModuleDependencyPlanner populates graph with module deps
  // and ContentModuleXmlWriter publishes the output used for validation.
  // Validation queries deps from the graph (EDGE_CONTENT_MODULE_DEPENDS_ON / EDGE_CONTENT_MODULE_DEPENDS_ON_TEST).
  override val requires: Set<DataSlot<*>> get() = setOf(Slots.CONTENT_MODULE)

  override suspend fun execute(ctx: ComputeContext) {
    val model = ctx.model

    val contentModuleResults = ctx.get(Slots.CONTENT_MODULE).files
    val productSourceFiles = buildProductSourceFiles(
      products = model.discovery.products,
      outputProvider = model.outputProvider,
      projectRoot = model.projectRoot,
    )

    val pluginAllowedMissing = model.config.pluginAllowedMissingDependencies
      .map { (k, v) -> TargetName(k.value) to v }
      .toMap()
    val dslTestPluginAdditionalBundles = LinkedHashMap<TargetName, Set<TargetName>>()
    for (spec in model.dslTestPluginsByProduct.values.flatten()) {
      if (spec.additionalBundledPluginTargetNames.isEmpty()) continue
      val key = TargetName(spec.pluginId.value)
      val merged = (dslTestPluginAdditionalBundles.get(key) ?: emptySet()) + spec.additionalBundledPluginTargetNames
      dslTestPluginAdditionalBundles.put(key, merged)
    }

    val allowedLibraryModulesByContentModule = buildAllowedLibraryModulesByContentModule(
      testLibraryAllowedInModule = model.config.testLibraryAllowedInModule,
      projectLibraryToModuleMap = model.config.projectLibraryToModuleMap,
    )

    val pluginDepErrors = validatePluginDependencies(
      pluginGraph = model.pluginGraph,
      contentModuleResults = contentModuleResults,
      descriptorCache = model.descriptorCache,
      pluginAllowedMissing = pluginAllowedMissing,
      productAllowedMissingByProduct = model.productAllowedMissing,
      suppressionConfig = model.suppressionConfig,
      allowedLibraryModulesByContentModule = allowedLibraryModulesByContentModule,
      dslTestPluginAdditionalBundles = dslTestPluginAdditionalBundles,
      productSourceFiles = productSourceFiles,
      projectRoot = model.projectRoot,
    )
    ctx.emitErrors(pluginDepErrors)
  }
}

// region Plugin Dependency Validation

/**
 * Validates plugin dependencies across all products.
 *
 * This ensures:
 * 1. Plugin dependencies are available in bundling products (availability check)
 * 2. ON_DEMAND/OPTIONAL deps exist somewhere (global existence check)
 * 3. Filtered dependencies resolve unless suppressed/allowed
 *
 * @param pluginGraph Unified graph model for plugin/product queries
 * @param contentModuleResults Results from content module dependency generation (used to filter implicit deps)
 * @param pluginAllowedMissing Per-plugin allowed missing deps
 * @param productAllowedMissingByProduct Product-level allowed missing deps (per product)
 * @param suppressionConfig Suppression config for checking suppressed module deps
 * @return List of validation errors
 */
internal suspend fun validatePluginDependencies(
  pluginGraph: PluginGraph,
  contentModuleResults: List<DependencyFileResult>,
  descriptorCache: ModuleDescriptorCache,
  pluginAllowedMissing: Map<TargetName, Set<ContentModuleName>>,
  productAllowedMissingByProduct: Map<String, Set<ContentModuleName>>,
  suppressionConfig: SuppressionConfig,
  allowedLibraryModulesByContentModule: Map<ContentModuleName, Set<ContentModuleName>> = emptyMap(),
  dslTestPluginAdditionalBundles: Map<TargetName, Set<TargetName>> = emptyMap(),
  productSourceFiles: Map<String, String> = emptyMap(),
  projectRoot: Path,
): List<ValidationError> {
  val contentModulesWithDescriptors = contentModuleResults.mapTo(HashSet()) { it.contentModuleName }
  val modulesWithDescriptors = HashSet(contentModulesWithDescriptors)
  val graphModules = LinkedHashSet<ContentModuleName>()
  pluginGraph.query {
    contentModules { module ->
      val moduleName = module.contentName()
      if (moduleName !in modulesWithDescriptors) {
        graphModules.add(moduleName)
      }
    }
  }
  for (moduleName in graphModules) {
    if (descriptorCache.getOrAnalyze(moduleName.value) != null) {
      modulesWithDescriptors.add(moduleName)
    }
  }
  return pluginGraph.query {
    val resolutionQuery = createResolutionQuery()

    val result = ArrayList<ValidationError>()
    plugins { plugin ->
      if (plugin.pluginIdOrNull == null) return@plugins

      val pluginName = plugin.name()
      val snapshot = collectPluginContentSnapshot(plugin)
      val productionContentModules = snapshot.productionContentModules
      val contentModulesInGraph = snapshot.allContentModules
      val loadingModes = snapshot.loadingModes
      val bundlingProducts = snapshot.bundlingProducts

      val moduleDeps = LinkedHashSet<ContentModuleName>()
      plugin.dependsOnContentModule { module ->
        val moduleName = module.contentName()
        if (moduleName !in contentModulesInGraph) {
          moduleDeps.add(moduleName)
        }
      }

      val isTestPlugin = plugin.isTest
      val contentModulesForValidation = snapshot.contentModulesForValidation(isTestPlugin)
      val contentModuleDeps = collectContentModuleDeps(contentModulesForValidation, isTestPlugin)

      val contentModuleFilteredDeps = LinkedHashMap<ContentModuleName, Set<ContentModuleName>>()
      for (moduleName in productionContentModules) {
        if (moduleName !in contentModulesWithDescriptors) continue
        val implicitDeps = computeImplicitDeps(moduleName)
          .filterTo(LinkedHashSet()) { dep -> dep in modulesWithDescriptors }
        val allowedLibraryModules = allowedLibraryModulesByContentModule.get(moduleName)
        val effectiveImplicitDeps = if (allowedLibraryModules.isNullOrEmpty()) {
          implicitDeps
        }
        else {
          implicitDeps.filterNotTo(LinkedHashSet()) { dep -> dep in allowedLibraryModules }
        }
        if (effectiveImplicitDeps.isNotEmpty()) {
          contentModuleFilteredDeps.put(moduleName, effectiveImplicitDeps)
        }
      }

      val error = validateSinglePlugin(
        pluginGraph = pluginGraph,
        pluginName = pluginName,
        contentModules = contentModulesForValidation,
        loadingModes = loadingModes,
        contentModuleDeps = contentModuleDeps,
        pluginModuleDependencies = moduleDeps,
        isTestPlugin = isTestPlugin,
        isDslDefined = plugin.isDslDefined,
        bundlingProducts = bundlingProducts,
        resolutionQuery = resolutionQuery,
        contentModuleFilteredDeps = contentModuleFilteredDeps,
        pluginAllowed = pluginAllowedMissing.get(pluginName) ?: emptySet(),
        productAllowedMissingByProduct = productAllowedMissingByProduct,
        suppressionConfig = suppressionConfig,
        dslTestPluginAdditionalBundles = dslTestPluginAdditionalBundles.get(pluginName) ?: emptySet(),
        includeTestSources = isTestPlugin,
        productSourceFiles = productSourceFiles,
        projectRoot = projectRoot,
      )
      if (error != null) {
        result.add(error)
      }
    }
    result
  }
}

/**
 * Validates a single plugin's dependencies.
 */
private fun validateSinglePlugin(
  pluginGraph: PluginGraph,
  pluginName: TargetName,
  contentModules: Set<ContentModuleName>,
  loadingModes: Map<ContentModuleName, ModuleLoadingRuleValue?>,
  contentModuleDeps: Map<ContentModuleName, Set<ContentModuleName>>,
  pluginModuleDependencies: Set<ContentModuleName>,
  isTestPlugin: Boolean,
  isDslDefined: Boolean,
  bundlingProducts: Set<String>,
  resolutionQuery: ResolutionQuery,
  contentModuleFilteredDeps: Map<ContentModuleName, Set<ContentModuleName>>,
  pluginAllowed: Set<ContentModuleName>,
  productAllowedMissingByProduct: Map<String, Set<ContentModuleName>>,
  suppressionConfig: SuppressionConfig,
  dslTestPluginAdditionalBundles: Set<TargetName>,
  includeTestSources: Boolean,
  productSourceFiles: Map<String, String>,
  projectRoot: Path,
): PluginDependencyError? {
  // Collect deps separated by loading mode
  val (requiredDeps, onDemandDeps) = collectDependenciesByLoadingMode(
    contentModules = contentModules,
    loadingModes = loadingModes,
    contentModuleDeps = contentModuleDeps,
    pluginModuleDependencies = pluginModuleDependencies,
  )

  var allowedInAllBundlingProducts: Set<ContentModuleName>? = null
  for (productName in bundlingProducts) {
    val allowedForProduct = productAllowedMissingByProduct.get(productName) ?: emptySet()
    allowedInAllBundlingProducts = allowedInAllBundlingProducts?.intersect(allowedForProduct) ?: allowedForProduct
  }
  val globalAllowed = pluginAllowed + (allowedInAllBundlingProducts ?: emptySet())

  // === Layer 1: Availability Validation ===
  val availabilityPredicate = when {
    isTestPlugin && isDslDefined -> forDslTestPlugin(pluginName, dslTestPluginAdditionalBundles)
    isTestPlugin -> forTestPlugin
    else -> forProductionPlugin
  }
  // DSL test plugins may depend on plugin-owned modules whose owning plugin is not resolvable in test scope.
  // Auto-add skips those modules, so allow them as missing per product while still warning at generation time.
  val dslAllowedMissingByProduct = if (isTestPlugin && isDslDefined && bundlingProducts.isNotEmpty()) {
    val result = HashMap<String, Set<ContentModuleName>>()
    for (productName in bundlingProducts) {
      val allowed = requiredDeps.filterTo(HashSet()) { dep ->
        val scan = resolutionQuery.scanSources(
          dep,
          availabilityPredicate,
          productName,
          trackPluginSource = true,
          includeTestSources = includeTestSources,
        )
        scan.hasPluginSource && !scan.matchesPredicate
      }
      if (allowed.isNotEmpty()) {
        result.put(productName, allowed)
      }
    }
    result
  }
  else {
    emptyMap()
  }
  val unresolvedByProduct = LinkedHashMap<String, Set<ContentModuleName>>()

  if (bundlingProducts.isEmpty()) {
    // Non-bundled plugin: check global existence
    val nonBundledPredicate = if (isTestPlugin) existsAnywhere else existsInNonTestSource
    val unresolved = resolutionQuery.findUnresolvedDeps(
      deps = requiredDeps,
      predicate = nonBundledPredicate,
      productName = "",
      allowedMissing = globalAllowed,
      includeTestSources = includeTestSources,
    )
    if (unresolved.isNotEmpty()) {
      unresolvedByProduct.put("(non-bundled)", unresolved.toSet())
    }
  }
  else {
    // Bundled plugin: check per-product availability
    for (productName in bundlingProducts) {
      val productAllowed = productAllowedMissingByProduct.get(productName) ?: emptySet()
      val combinedAllowed = productAllowed + pluginAllowed + (dslAllowedMissingByProduct.get(productName) ?: emptySet())
      val unresolved = resolutionQuery.findUnresolvedDeps(
        deps = requiredDeps,
        predicate = availabilityPredicate,
        productName = productName,
        allowedMissing = combinedAllowed,
        includeTestSources = includeTestSources,
      )
      if (unresolved.isNotEmpty()) {
        unresolvedByProduct.put(productName, unresolved.toSet())
      }
    }
  }

  // === Layer 2: ON_DEMAND Global Existence ===
  val unknownOnDemandDeps = resolutionQuery.findUnresolvedDeps(
    deps = onDemandDeps,
    predicate = existsAnywhere,
    productName = "",
    allowedMissing = globalAllowed,
    includeTestSources = includeTestSources,
  )

  // === Layer 3: Filtered Dependency Validation ===
  val unresolvedFilteredDeps = LinkedHashMap<ContentModuleName, MutableSet<ContentModuleName>>()
  for ((moduleName, filteredDeps) in contentModuleFilteredDeps) {
    if (moduleName !in contentModules) continue
    val suppressedModules = suppressionConfig.getSuppressedModules(moduleName)
    for (filteredDep in filteredDeps) {
      if (filteredDep in suppressedModules) continue
      if (filteredDep in globalAllowed) continue
      val scan = resolutionQuery.scanSources(filteredDep, existsAnywhere, "", includeTestSources = includeTestSources)
      if (!scan.matchesPredicate) {
        unresolvedFilteredDeps.computeIfAbsent(moduleName) { LinkedHashSet() }.add(filteredDep)
      }
    }
  }

  // === Error Reporting ===
  val hasAvailabilityErrors = unresolvedByProduct.isNotEmpty()
  val hasOnDemandErrors = unknownOnDemandDeps.isNotEmpty()
  val hasFilteredDepErrors = unresolvedFilteredDeps.isNotEmpty()

  if (!hasAvailabilityErrors && !hasOnDemandErrors && !hasFilteredDepErrors) {
    return null
  }

  // Build missing deps map for error message
  val missingDeps = HashMap<ContentModuleName, MutableSet<ContentModuleName>>()
  val allProblematic = unresolvedByProduct.values.flatten().toSet() + unknownOnDemandDeps

  for (dep in allProblematic) {
    for (contentModuleName in contentModules) {
      val deps = contentModuleDeps.get(contentModuleName) ?: emptySet()
      val testDeps = contentModuleDeps.get(ContentModuleName("${contentModuleName.value}._test")) ?: emptySet()
      if (dep in deps) {
        missingDeps.computeIfAbsent(dep) { HashSet() }.add(contentModuleName)
      }
      if (dep in testDeps) {
        missingDeps.computeIfAbsent(dep) { HashSet() }.add(ContentModuleName("${contentModuleName.value}._test"))
      }
    }
    if (dep in pluginModuleDependencies) {
      missingDeps.computeIfAbsent(dep) { HashSet() }.add(ContentModuleName("plugin.xml"))
    }
  }

  // Add unresolved filtered deps to missing deps map
  for ((moduleName, filteredDeps) in unresolvedFilteredDeps) {
    for (filteredDep in filteredDeps) {
      missingDeps.computeIfAbsent(filteredDep) { HashSet() }.add(moduleName)
    }
  }

  val pluginFilteredDeps = contentModuleFilteredDeps.filterKeys { it in contentModules }

  val moduleSourceInfo = buildModuleSourceInfo(
    pluginGraph = pluginGraph,
    modules = allProblematic + missingDeps.values.flatten(),
    loadingModes = loadingModes,
    pluginName = pluginName,
    pluginContentModules = contentModules,
    isTestPlugin = isTestPlugin,
    bundlingProducts = bundlingProducts,
  )

  val proposedPatches = buildAllowedMissingDependencyPatches(
    unresolvedByProduct = unresolvedByProduct,
    bundlingProducts = bundlingProducts,
    missingModules = missingDeps.keys,
    productSourceFiles = productSourceFiles,
    projectRoot = projectRoot,
    pluginName = pluginName,
    isTestPlugin = isTestPlugin,
    isDslDefined = isDslDefined,
  )

  return PluginDependencyError(
    context = pluginName.value,
    pluginName = pluginName,
    missingDependencies = missingDeps,
    moduleSourceInfo = moduleSourceInfo,
    unresolvedByProduct = unresolvedByProduct,
    filteredDependencies = pluginFilteredDeps,
    proposedPatches = proposedPatches,
  )
}

/**
 * Build ModuleSourceInfo map from graph-derived sources.
 */
private fun buildModuleSourceInfo(
  pluginGraph: PluginGraph,
  modules: Set<ContentModuleName>,
  loadingModes: Map<ContentModuleName, ModuleLoadingRuleValue?>,
  pluginName: TargetName,
  pluginContentModules: Set<ContentModuleName>,
  isTestPlugin: Boolean,
  bundlingProducts: Set<String>,
): Map<ContentModuleName, ModuleSourceInfo> {
  val result = HashMap<ContentModuleName, ModuleSourceInfo>()

  for (moduleName in modules) {
    val info = getModuleSourceInfo(pluginGraph, moduleName) ?: continue
    result.put(moduleName, info)
  }

  // Add/override info for content modules of the plugin being validated
  for (contentModule in pluginContentModules) {
    result.put(contentModule, ModuleSourceInfo(
      loadingMode = loadingModes.get(contentModule),
      sourcePlugin = pluginName,
      isTestPlugin = isTestPlugin,
      bundledInProducts = bundlingProducts,
    ))
  }

  return result
}

private fun buildProductSourceFiles(
  products: List<DiscoveredProduct>,
  outputProvider: ModuleOutputProvider,
  projectRoot: Path,
): Map<String, String> {
  val result = LinkedHashMap<String, String>()
  for (product in products) {
    val props = product.properties
    val sourceFile = if (props == null) {
      "test-product"
    }
    else {
      try {
        findProductPropertiesSourceFile(
          buildModules = product.config.modules,
          productPropertiesClass = props.javaClass,
          outputProvider = outputProvider,
          projectRoot = projectRoot,
        )
      }
      catch (_: Exception) {
        "${props.javaClass.name} (source file not found)"
      }
    }
    result.put(product.name, sourceFile)
  }
  return result
}

private fun buildAllowedMissingDependencyPatches(
  unresolvedByProduct: Map<String, Set<ContentModuleName>>,
  bundlingProducts: Set<String>,
  missingModules: Set<ContentModuleName>,
  productSourceFiles: Map<String, String>,
  projectRoot: Path,
  pluginName: TargetName,
  isTestPlugin: Boolean,
  isDslDefined: Boolean,
): List<ProposedPatch> {
  if (missingModules.isEmpty()) return emptyList()

  val productsToPatch = when {
    unresolvedByProduct.isNotEmpty() -> unresolvedByProduct
    bundlingProducts.isNotEmpty() -> bundlingProducts.associateWith { missingModules }
    else -> emptyMap()
  }
  if (productsToPatch.isEmpty()) return emptyList()

  val patches = ArrayList<ProposedPatch>()
  for ((productName, missingModulesForProduct) in productsToPatch.entries.sortedBy { it.key }) {
    if (productName == "(non-bundled)") continue
    if (missingModulesForProduct.isEmpty()) continue

    val sourceFile = productSourceFiles.get(productName) ?: continue
    if (sourceFile == "test-product") continue

    val patch = when {
      isTestPlugin && isDslDefined -> buildTestPluginContentPatch(
        sourceFile = sourceFile,
        pluginId = pluginName.value,
        missingModules = missingModulesForProduct,
        projectRoot = projectRoot,
      )
      !isTestPlugin -> buildAllowMissingDependenciesPatch(
        sourceFile = sourceFile,
        missingModules = missingModulesForProduct,
        projectRoot = projectRoot,
      )
      else -> null
    } ?: continue

    patches.add(ProposedPatch(
      title = "$productName ($sourceFile)",
      patch = patch,
    ))
  }
  return patches
}

private fun buildTestPluginContentPatch(
  sourceFile: String,
  pluginId: String,
  missingModules: Set<ContentModuleName>,
  projectRoot: Path,
): String? {
  val filePath = projectRoot.resolve(sourceFile)
  if (!Files.exists(filePath)) return null

  val lines = Files.readAllLines(filePath)
  val pluginIdIndex = lines.indexOfFirst { line ->
    line.contains("pluginId") && line.contains("\"$pluginId\"")
  }
  if (pluginIdIndex < 0) return null

  var testPluginIndex = pluginIdIndex
  while (testPluginIndex >= 0 && !lines[testPluginIndex].contains("testPlugin(")) {
    testPluginIndex--
  }
  if (testPluginIndex < 0) return null

  var blockStartIndex = pluginIdIndex
  while (blockStartIndex < lines.size && !lines[blockStartIndex].contains("{")) {
    blockStartIndex++
  }
  if (blockStartIndex >= lines.size) return null

  val line = lines[blockStartIndex]
  val lineNumber = blockStartIndex + 1
  val baseIndent = line.takeWhile { it == ' ' || it == '\t' }
  val indentUnit = if (baseIndent.contains('\t')) "\t" else "  "
  val indent = baseIndent + indentUnit
  val sortedModules = missingModules.map { it.value }.sorted()
  val insertedLines = sortedModules.map { module -> "${indent}module(\"$module\")" }
  return buildInsertPatch(
    sourceFile = sourceFile,
    lineNumber = lineNumber,
    line = line,
    insertedLines = insertedLines,
  )
}

private fun buildAllowMissingDependenciesPatch(
  sourceFile: String,
  missingModules: Set<ContentModuleName>,
  projectRoot: Path,
): String? {
  val filePath = projectRoot.resolve(sourceFile)
  if (!Files.exists(filePath)) return null

  val lines = Files.readAllLines(filePath)
  val productModulesIndex = findProductModulesLineIndex(lines)
  if (productModulesIndex < 0) return null

  val line = lines[productModulesIndex]
  val lineNumber = productModulesIndex + 1
  val baseIndent = line.takeWhile { it == ' ' || it == '\t' }
  val indentUnit = if (baseIndent.contains('\t')) "\t" else "  "
  val indent = baseIndent + indentUnit
  val innerIndent = indent + indentUnit
  val sortedModules = missingModules.map { it.value }.sorted()
  val insertedLines = ArrayList<String>(sortedModules.size + 2)
  insertedLines.add("${indent}allowMissingDependencies(")
  for (module in sortedModules) {
    insertedLines.add("${innerIndent}\"$module\",")
  }
  insertedLines.add("${indent})")
  return buildInsertPatch(
    sourceFile = sourceFile,
    lineNumber = lineNumber,
    line = line,
    insertedLines = insertedLines,
  )
}

private fun buildInsertPatch(
  sourceFile: String,
  lineNumber: Int,
  line: String,
  insertedLines: List<String>,
): String {
  val newCount = 1 + insertedLines.size
  val patch = StringBuilder()
  patch.appendLine("--- a/$sourceFile")
  patch.appendLine("+++ b/$sourceFile")
  patch.appendLine("@@ -$lineNumber,1 +$lineNumber,$newCount @@")
  patch.appendLine(" $line")
  for (insertedLine in insertedLines) {
    patch.appendLine("+$insertedLine")
  }
  return patch.toString().trimEnd()
}

private fun findProductModulesLineIndex(lines: List<String>): Int {
  val descriptorIndex = lines.indexOfFirst { line ->
    line.contains("getProductContentDescriptor") || line.contains("getProductContentModules")
  }
  val searchStart = if (descriptorIndex >= 0) descriptorIndex else 0
  val regex = Regex("\\bproductModules\\s*\\{")

  for (i in searchStart until lines.size) {
    if (regex.containsMatchIn(lines[i])) return i
  }

  if (descriptorIndex >= 0) return -1

  for (i in lines.indices) {
    if (regex.containsMatchIn(lines[i])) return i
  }
  return -1
}

// endregion

// region Structural Violation Auto-Fix

// Map allowed test libraries to their library module content names for dependency filtering.
private fun buildAllowedLibraryModulesByContentModule(
  testLibraryAllowedInModule: Map<ContentModuleName, Set<String>>,
  projectLibraryToModuleMap: Map<String, String>,
): Map<ContentModuleName, Set<ContentModuleName>> {
  if (testLibraryAllowedInModule.isEmpty()) return emptyMap()

  val result = LinkedHashMap<ContentModuleName, Set<ContentModuleName>>()
  for ((moduleName, allowedLibraries) in testLibraryAllowedInModule) {
    val allowedModules = LinkedHashSet<ContentModuleName>()
    for (libraryName in allowedLibraries) {
      val libraryModuleName = when {
        libraryName.startsWith(LIB_MODULE_PREFIX) -> libraryName
        else -> projectLibraryToModuleMap[libraryName]
      }
      if (libraryModuleName != null && libraryModuleName.startsWith(LIB_MODULE_PREFIX)) {
        allowedModules.add(ContentModuleName(libraryModuleName))
      }
    }
    if (allowedModules.isNotEmpty()) {
      result[moduleName] = allowedModules
    }
  }
  return result
}

/**
 * Computes implicit (filtered) dependencies for a content module.
 *
 * Implicit deps = JPS deps missing from XML:
 * - JPS deps (from EDGE_TARGET_DEPENDS_ON via backedBy traversal, excluding TEST scope)
 * - XML deps (from EDGE_CONTENT_MODULE_DEPENDS_ON edges)
 *
 * This represents dependencies filtered out during generation:
 * - JPS deps not in XML = would be added without suppression
 *
 * XML deps not in JPS are explicit declarations and are validated as regular deps,
 * so they are intentionally excluded from this implicit set.
 *
 * **TEST scope filtering**: JPS dependencies with TEST scope are excluded from the
 * comparison because they're handled separately via EDGE_CONTENT_MODULE_DEPENDS_ON_TEST.
 * Production XML deps (EDGE_CONTENT_MODULE_DEPENDS_ON) should only be compared against
 * production JPS deps (COMPILE/RUNTIME scope).
 *
 * Use this during validation instead of passing `allJpsDependencies` through results.
 *
 * @param moduleName The content module name
 * @return Set of implicit dependencies (JPS deps missing from XML)
 */
internal fun GraphScope.computeImplicitDeps(moduleName: ContentModuleName): Set<ContentModuleName> {
  val contentModule = contentModule(moduleName) ?: return emptySet()

  // Collect JPS deps via Module --backedBy--> Target --dependsOn--> classifyTarget()
  // Filter out TEST scope deps (they're handled by EDGE_CONTENT_MODULE_DEPENDS_ON_TEST)
  val jpsDeps = HashSet<ContentModuleName>()
  contentModule.backedBy { target ->
    target.dependsOn { dep ->
      // Skip TEST and PROVIDED scope deps - they're not expected in production XML
      // PRODUCTION_RUNTIME includes only COMPILE and RUNTIME scopes
      if (!dep.isProduction()) return@dependsOn

      when (val c = classifyTarget(dep.targetId)) {
        is DependencyClassification.ModuleDep -> {
          if (c.moduleName != moduleName) {
            jpsDeps.add(c.moduleName)
          }
        }
        else -> {}
      }
    }
  }

  // Collect XML deps via EDGE_CONTENT_MODULE_DEPENDS_ON
  val xmlDeps = HashSet<ContentModuleName>()
  contentModule.dependsOn { dep -> xmlDeps.add(dep.contentName()) }

  // Return JPS deps filtered out from XML generation
  return jpsDeps - xmlDeps
}
