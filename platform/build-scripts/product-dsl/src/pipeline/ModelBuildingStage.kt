// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "GrazieInspection", "GrazieStyle", "ReplaceGetOrSet")

package org.jetbrains.intellij.build.productLayout.pipeline

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.EDGE_ALLOWS_MISSING
import com.intellij.platform.pluginGraph.EDGE_BUNDLES
import com.intellij.platform.pluginGraph.PluginGraph
import com.intellij.platform.pluginGraph.PluginId
import com.intellij.platform.pluginGraph.TargetName
import com.intellij.platform.pluginGraph.baseModuleName
import com.intellij.platform.pluginGraph.isSlashNotation
import com.intellij.platform.pluginGraph.isTestDescriptor
import com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRuleValue
import com.intellij.platform.plugins.parser.impl.parseContentAndXIncludes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.PLUGIN_XML_RELATIVE_PATH
import org.jetbrains.intellij.build.findFileInModuleDependenciesRecursiveAsync
import org.jetbrains.intellij.build.findFileInModuleLibraryDependencies
import org.jetbrains.intellij.build.findFileInModuleSources
import org.jetbrains.intellij.build.productLayout.ContentModule
import org.jetbrains.intellij.build.productLayout.DeprecatedXmlInclude
import org.jetbrains.intellij.build.productLayout.ProductModulesContentSpec
import org.jetbrains.intellij.build.productLayout.TestPluginSpec
import org.jetbrains.intellij.build.productLayout.buildContentBlocksAndChainMapping
import org.jetbrains.intellij.build.productLayout.collectAndValidateAliases
import org.jetbrains.intellij.build.productLayout.config.SuppressionConfig
import org.jetbrains.intellij.build.productLayout.debug
import org.jetbrains.intellij.build.productLayout.dependency.ModuleDescriptorCache
import org.jetbrains.intellij.build.productLayout.dependency.PluginContentCache
import org.jetbrains.intellij.build.productLayout.deps.collectResolvableModules
import org.jetbrains.intellij.build.productLayout.discovery.ModuleSetGenerationConfig
import org.jetbrains.intellij.build.productLayout.discovery.PluginContentInfo
import org.jetbrains.intellij.build.productLayout.discovery.computePluginContentFromDslSpec
import org.jetbrains.intellij.build.productLayout.graph.PluginGraphBuilder
import org.jetbrains.intellij.build.productLayout.model.ErrorSink
import org.jetbrains.intellij.build.productLayout.model.error.DuplicateDslTestPluginIdError
import org.jetbrains.intellij.build.productLayout.stats.SuppressionUsage
import org.jetbrains.intellij.build.productLayout.traversal.collectPluginContentModules
import org.jetbrains.intellij.build.productLayout.traversal.collectProductModuleNames
import org.jetbrains.intellij.build.productLayout.util.AsyncCache
import org.jetbrains.intellij.build.productLayout.util.DeferredFileUpdater
import org.jetbrains.intellij.build.productLayout.util.XmlWritePolicy
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.util.JpsPathUtil
import java.nio.file.Files
import java.nio.file.Path

private val CORE_PLUGIN_ID = PluginId("com.intellij")
private val CORE_PLUGIN_NODE_NAME = TargetName("__core__:com.intellij")
private val OS_MODULE_ALIASES = listOf(
  PluginId("com.intellij.modules.os.freebsd"),
  PluginId("com.intellij.modules.os.linux"),
  PluginId("com.intellij.modules.os.mac"),
  PluginId("com.intellij.modules.os.unix"),
  PluginId("com.intellij.modules.os.windows"),
  PluginId("com.intellij.modules.os.xwindow"),
)

/**
 * Stage 2: Model Building
 *
 * Creates all caches and computes shared values needed by generators:
 * - Descriptor cache for async module descriptor analysis
 * - Plugin content cache (pre-warmed with bundled plugins)
 * - Shared Deferred values for parallel access
 *
 * **Input:** [DiscoveryResult] + [ModuleSetGenerationConfig]
 * **Output:** [GenerationModel]
 *
 * **Key design:** All expensive computations happen here once.
 * Generators receive the model and don't recompute anything.
 */
internal object ModelBuildingStage {
  /**
   * Executes the model building stage.
   *
   * @param discovery Results from discovery stage
   * @param config Generation configuration
   * @param scope Coroutine scope for async operations
   * @param errorSink Sink for errors discovered during model building (e.g., xi:include resolution)
   * @return Fully initialized generation model
   */
  suspend fun execute(
    discovery: DiscoveryResult,
    config: ModuleSetGenerationConfig,
    scope: CoroutineScope,
    updateSuppressions: Boolean,
    commitChanges: Boolean,
    errorSink: ErrorSink,
  ): GenerationModel {
    val projectRoot = config.projectRoot
    val outputProvider = config.outputProvider
    val isUltimateBuild = Files.exists(projectRoot.resolve("community"))

    // Load suppression config from path (single source of truth)
    val suppressionConfig = SuppressionConfig.load(config.suppressionConfigPath)

    // Create file updater for deferred writes
    val fileUpdater = DeferredFileUpdater(projectRoot)
    val generationMode = when {
      updateSuppressions -> GenerationMode.UPDATE_SUPPRESSIONS
      !commitChanges -> GenerationMode.VALIDATE_ONLY
      else -> GenerationMode.NORMAL
    }
    val xmlWritePolicy = XmlWritePolicy(generationMode, fileUpdater)

    // Create xi:include cache (shared across plugin content extraction)
    val xIncludeCache = AsyncCache<String, ByteArray?>(scope)

    // Create plugin content cache
    // ErrorSink is used to emit xi:include errors during plugin content extraction
    val pluginContentCache = PluginContentCache(
      outputProvider = outputProvider,
      xIncludeCache = xIncludeCache,
      skipXIncludePaths = config.skipXIncludePaths,
      xIncludePrefixFilter = config.xIncludePrefixFilter,
      scope = scope,
      errorSink = errorSink,
    )

    // Build lookup for DSL-defined test plugins keyed by PluginId (semantically correct)
    // Note: PluginId is the XML plugin identifier, distinct from ModuleName (JPS module)
    val dslTestPluginsByProduct = discovery.products
      .mapNotNull { product ->
        val testPlugins = product.spec?.testPlugins?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        product.name to testPlugins
      }
      .toMap()
    val dslTestPluginIdOwners = LinkedHashMap<PluginId, MutableList<String>>()
    for ((productName, testPlugins) in dslTestPluginsByProduct) {
      for (spec in testPlugins) {
        dslTestPluginIdOwners.getOrPut(spec.pluginId) { ArrayList() }.add(productName)
      }
    }
    for ((pluginId, owners) in dslTestPluginIdOwners) {
      if (owners.size <= 1) continue
      errorSink.emit(DuplicateDslTestPluginIdError(
        context = pluginId.value,
        pluginId = pluginId,
        productCounts = owners.groupingBy { it }.eachCount(),
      ))
    }
    val dslTestPlugins = dslTestPluginsByProduct.values.flatten()
    val dslTestPluginIds: Set<PluginId> = dslTestPlugins.mapTo(HashSet()) { it.pluginId }
    val dslTestPluginAdditionalBundles: Set<TargetName> = dslTestPlugins.asSequence()
      .flatMap { it.additionalBundledPluginTargetNames.asSequence() }
      .toSet()

    // Create descriptor cache
    val descriptorCache = ModuleDescriptorCache(outputProvider = outputProvider, scope = scope)

    // Build unified graph model for plugin/module/product relationships
    // Graph is the single source of truth - built DURING extraction
    val builder = PluginGraphBuilder(errorSink = errorSink)
    val pluginInfos = LinkedHashMap<TargetName, PluginContentInfo>()

    val extraPluginDescriptors = if (config.includeTestPluginDescriptorsFromSources) {
      discoverPluginDescriptorsFromSources(outputProvider)
    }
    else {
      DiscoveredPluginDescriptors(emptySet(), emptySet())
    }
    val testPluginModuleNames = config.testPluginsByProduct.values.flatten().toHashSet()
    testPluginModuleNames.addAll(extraPluginDescriptors.testPluginModules)
    seedPluginsForExtraction(
      discovery = discovery,
      config = config,
      builder = builder,
      dslTestPluginIds = dslTestPluginIds,
      dslTestPluginAdditionalBundles = dslTestPluginAdditionalBundles,
      testPluginModuleNames = testPluginModuleNames,
      extraPluginModules = extraPluginDescriptors.pluginModules,
    )
    val pluginsToExtract = collectSeededPluginTargets(builder.build())
    extractPlugins(
      pluginTargets = pluginsToExtract,
      pluginContentCache = pluginContentCache,
      builder = builder,
      pluginInfos = pluginInfos,
      testPluginModuleNames = testPluginModuleNames,
      testFrameworkContentModules = config.testFrameworkContentModules,
    )

    val includeAliasCache = AsyncCache<String, Set<PluginId>>(scope)
    val moduleDescriptorAliasCache = AsyncCache<ContentModuleName, Set<PluginId>>(scope)
    linkProductsAndBundledPlugins(discovery, builder)
    linkTestPluginsByProduct(config, builder)
    addModuleSets(discovery, builder)
    val baseGraphView = builder.build()
    linkProductAliases(
      discovery = discovery,
      config = config,
      builder = builder,
      graphView = baseGraphView,
      outputProvider = outputProvider,
      isUltimateBuild = isUltimateBuild,
      descriptorCache = descriptorCache,
      includeAliasCache = includeAliasCache,
      moduleDescriptorAliasCache = moduleDescriptorAliasCache,
      pluginInfos = pluginInfos,
    )
    seedDslTestPluginTargets(builder, dslTestPluginsByProduct)
    addJpsDependencies(builder, outputProvider, config.projectLibraryToModuleMap)
    registerReferencedPlugins(builder, pluginContentCache, pluginInfos)
    builder.markDescriptorModules(descriptorCache)
    val graphWithJpsDeps = builder.build()

    val dslTestPluginExpansion = expandDslTestPlugins(
      discovery = discovery,
      config = config,
      builder = builder,
      graphView = graphWithJpsDeps,
      pluginContentCache = pluginContentCache,
      dslTestPluginsByProduct = dslTestPluginsByProduct,
      descriptorCache = descriptorCache,
      suppressionConfig = suppressionConfig,
      updateSuppressions = updateSuppressions,
      projectRoot = projectRoot,
      errorSink = errorSink,
    )
    addJpsDependencies(builder, outputProvider, config.projectLibraryToModuleMap)
    registerReferencedPlugins(builder, pluginContentCache, pluginInfos)
    builder.markDescriptorModules(descriptorCache)
    addPluginDependencyEdges(builder, pluginInfos)

    val pluginGraph = builder.buildFrozen()

    // Build per-product allowedMissingDependencies map
    val productAllowedMissing = discovery.products
      .mapNotNull { d -> d.spec?.allowedMissingDependencies?.let { d.name to it } }
      .toMap()

    return GenerationModel(
      discovery = discovery,
      config = config,
      projectRoot = projectRoot,
      outputProvider = outputProvider,
      isUltimateBuild = isUltimateBuild,
      descriptorCache = descriptorCache,
      pluginContentCache = pluginContentCache,
      fileUpdater = fileUpdater,
      xmlWritePolicy = xmlWritePolicy,
      scope = scope,
      pluginGraph = pluginGraph,
      dslTestPluginsByProduct = dslTestPluginExpansion.pluginsByProduct,
      dslTestPluginDependencyChains = dslTestPluginExpansion.dependencyChains,
      dslTestPluginSuppressionUsages = dslTestPluginExpansion.suppressionUsages,
      productAllowedMissing = productAllowedMissing,
      suppressionConfig = suppressionConfig,
      updateSuppressions = updateSuppressions,
      generationMode = generationMode,
    )
  }

  private data class DslTestPluginExpansionResult(
    val pluginsByProduct: Map<String, List<TestPluginSpec>>,
    val suppressionUsages: List<SuppressionUsage>,
    val dependencyChains: Map<PluginId, Map<ContentModuleName, List<ContentModuleName>>>,
  )

  private suspend fun extractPlugins(
    pluginTargets: List<TargetName>,
    pluginContentCache: PluginContentCache,
    builder: PluginGraphBuilder,
    pluginInfos: MutableMap<TargetName, PluginContentInfo>,
    testPluginModuleNames: Set<TargetName>,
    testFrameworkContentModules: Set<ContentModuleName>,
  ) {
    // ═══════════════════════════════════════════════════════════════════════════════
    // Phase 1: Plugin Extraction
    // ═══════════════════════════════════════════════════════════════════════════════
    // PURPOSE: Extract all plugins from META-INF/plugin.xml to establish plugin nodes
    //          with correct pluginId BEFORE any bundling happens.
    //
    // INVARIANT: After this phase, all valid plugins have NODE_PLUGIN vertices with:
    //   - pluginId set (from <id> element in plugin.xml)
    //   - EDGE_MAIN_TARGET linking to their JPS module
    //   - EDGE_CONTAINS_CONTENT for all <content><module> entries
    //
    // DEPENDS ON: Nothing - this is the first graph-building phase
    //
    // NOTE: Plugins without META-INF/plugin.xml are silently skipped here.
    //       Phase 2 will emit MissingPluginInGraphError if a product tries to bundle them.
    // ───────────────────────────────────────────────────────────────────────────────
    val extractedPlugins = coroutineScope {
      pluginTargets.map { plugin ->
        async {
            val info = pluginContentCache.extract(plugin = plugin, isTest = plugin in testPluginModuleNames)
            info?.let { plugin to it }
        }
      }.awaitAll().filterNotNull()
    }
    for ((pluginModule, info) in extractedPlugins) {
      builder.addPluginWithContent(pluginModule = pluginModule, content = info, testFrameworkContentModules = testFrameworkContentModules)
      pluginInfos[pluginModule] = info
    }
  }

  private fun linkProductsAndBundledPlugins(
    discovery: DiscoveryResult,
    builder: PluginGraphBuilder,
  ) {
    // ═══════════════════════════════════════════════════════════════════════════════
    // Phase 2: Products and Bundled Plugins
    // ═══════════════════════════════════════════════════════════════════════════════
    // PURPOSE: Create product nodes and link them to plugins extracted in Phase 1.
    //
    // INVARIANT: After this phase:
    //   - All products have NODE_PRODUCT vertices
    //   - EDGE_BUNDLES links products to their bundled plugins
    //   - EDGE_INCLUDES_MODULE_SET links products to module sets
    //   - EDGE_CONTAINS_CONTENT links products to additional content modules
    //   - EDGE_ALLOWS_MISSING marks allowed missing dependencies
    //
    // DEPENDS ON: Phase 1 (plugins must exist before bundling)
    //
    // ERROR HANDLING: If a product tries to bundle a plugin that wasn't extracted
    //                 in Phase 1, emits MissingPluginInGraphError via ErrorSink.
    // ───────────────────────────────────────────────────────────────────────────────
    val corePluginNodeId = builder.addPlugin(name = CORE_PLUGIN_NODE_NAME, isTest = false, pluginId = CORE_PLUGIN_ID)

    for (product in discovery.products) {
      val productId = builder.addProduct(product.name)
      builder.addEdge(productId, corePluginNodeId, EDGE_BUNDLES)

      val spec = product.spec ?: continue

      // Bundled plugins - addPlugin finds existing nodes created in Phase 1
      for (pluginModule in spec.bundledPlugins) {
        builder.linkProductBundlesPlugin(productName = product.name, pluginName = pluginModule, isTest = false)
      }

      // Module sets
      for (moduleSetWithOverrides in spec.moduleSets) {
        builder.linkProductIncludesModuleSet(product.name, moduleSetWithOverrides.moduleSet.name)
      }

      // Additional modules (product content)
      for ((moduleName, loadingMode) in spec.additionalModules) {
        builder.linkProductContainsContent(product.name, moduleName, loadingMode)
      }

      // allowed missing dependencies (for validation)
      for (allowedModule in spec.allowedMissingDependencies) {
        builder.addEdge(source = productId, target = builder.addModule(allowedModule), edgeType = EDGE_ALLOWS_MISSING)
      }
    }
  }

  private fun linkTestPluginsByProduct(
    config: ModuleSetGenerationConfig,
    builder: PluginGraphBuilder,
  ) {
    // ═══════════════════════════════════════════════════════════════════════════════
    // Phase 3: Test Plugins by Product
    // ═══════════════════════════════════════════════════════════════════════════════
    // PURPOSE: Link test plugins to products via EDGE_BUNDLES_TEST.
    //
    // INVARIANT: After this phase, products have EDGE_BUNDLES_TEST to their test plugins.
    //
    // DEPENDS ON: Phase 1 (test plugins must be extracted first)
    // ───────────────────────────────────────────────────────────────────────────────
    for ((productName, testPlugins) in config.testPluginsByProduct) {
      for (pluginModule in testPlugins) {
        builder.linkProductBundlesPlugin(productName = productName, pluginName = pluginModule, isTest = true)
      }
    }
  }

  private fun addModuleSets(
    discovery: DiscoveryResult,
    builder: PluginGraphBuilder,
  ) {
    // ═══════════════════════════════════════════════════════════════════════════════
    // Phase 4: Module Sets
    // ═══════════════════════════════════════════════════════════════════════════════
    // PURPOSE: Add module set vertices and their content modules to the graph.
    //
    // INVARIANT: After this phase:
    //   - All module sets have NODE_MODULE_SET vertices
    //   - EDGE_CONTAINS_MODULE links module sets to their content modules
    //   - EDGE_NESTED_SET links parent sets to nested sets
    //   - EDGE_BACKED_BY links content modules to their backing JPS targets
    //
    // DEPENDS ON: Nothing (independent of plugin phases)
    // ───────────────────────────────────────────────────────────────────────────────
    for (moduleSet in discovery.allModuleSets) {
      builder.addModuleSetContent(moduleSet)
    }
  }

  private suspend fun linkProductAliases(
    discovery: DiscoveryResult,
    config: ModuleSetGenerationConfig,
    builder: PluginGraphBuilder,
    graphView: PluginGraph,
    outputProvider: ModuleOutputProvider,
    isUltimateBuild: Boolean,
    descriptorCache: ModuleDescriptorCache,
    includeAliasCache: AsyncCache<String, Set<PluginId>>,
    moduleDescriptorAliasCache: AsyncCache<ContentModuleName, Set<PluginId>>,
    pluginInfos: Map<TargetName, PluginContentInfo>,
  ) {
    // ═══════════════════════════════════════════════════════════════════════════════
    // Phase 4b: Product Aliases
    // ═══════════════════════════════════════════════════════════════════════════════
    // PURPOSE: Resolve product-level plugin aliases from module sets, deprecated includes,
    //          and bundled plugin descriptors, then link products to alias plugin nodes.
    //
    // DEPENDS ON: Phase 2 (product edges) + Phase 4 (module sets added)
    // ───────────────────────────────────────────────────────────────────────────────
    fun aliasNodeName(alias: PluginId): TargetName = TargetName("__alias__:${alias.value}")
    fun linkProductBundlesAlias(productName: String, alias: PluginId) {
      val productId = builder.addProduct(productName)
      val aliasNodeId = builder.addPlugin(name = aliasNodeName(alias), isTest = false, pluginId = alias)
      builder.addEdge(productId, aliasNodeId, EDGE_BUNDLES)
    }

    data class ProductAliasResult(
      val productName: String,
      val aliases: Set<PluginId>,
    )

    val aliasResults = coroutineScope {
      discovery.products.map { product ->
        async {
          val spec = product.spec ?: return@async null

          val aliasIds = LinkedHashSet<PluginId>()
          aliasIds.addAll(OS_MODULE_ALIASES)
          val moduleSetAliases = buildContentBlocksAndChainMapping(spec, collectModuleSetAliases = true).aliasToSource
          aliasIds.addAll(collectAndValidateAliases(spec, moduleSetAliases))
          aliasIds.addAll(collectAliasesFromDeprecatedIncludes(
            spec,
            outputProvider,
            isUltimateBuild,
            includeAliasCache,
            config.xIncludePrefixFilter,
            config.skipXIncludePaths,
          ))

          val productModuleNames = collectProductModuleNames(graphView, product.name)
            .toCollection(LinkedHashSet())
          aliasIds.addAll(collectAliasesFromModuleDescriptors(productModuleNames, descriptorCache, moduleDescriptorAliasCache))

          for (pluginModule in spec.bundledPlugins) {
            val info = pluginInfos[pluginModule]
            if (info != null) {
              if (info.pluginAliases.isNotEmpty()) {
                aliasIds.addAll(info.pluginAliases)
              }
              if (info.contentModules.isNotEmpty()) {
                val pluginModuleNames = info.contentModules.mapTo(LinkedHashSet()) { it.name }
                aliasIds.addAll(collectAliasesFromModuleDescriptors(pluginModuleNames, descriptorCache, moduleDescriptorAliasCache))
              }
            }
          }
          if (aliasIds.isNotEmpty()) {
            debug("aliasGraph") { "product=${product.name} aliases=${aliasIds.joinToString { it.value }}" }
          }
          ProductAliasResult(product.name, aliasIds)
        }
      }.awaitAll().filterNotNull()
    }

    for (result in aliasResults) {
      for (alias in result.aliases) {
        linkProductBundlesAlias(result.productName, alias)
      }
    }
  }

  private suspend fun expandDslTestPlugins(
    discovery: DiscoveryResult,
    config: ModuleSetGenerationConfig,
    builder: PluginGraphBuilder,
    graphView: PluginGraph,
    pluginContentCache: PluginContentCache,
    dslTestPluginsByProduct: Map<String, List<TestPluginSpec>>,
    descriptorCache: ModuleDescriptorCache,
    suppressionConfig: SuppressionConfig,
    updateSuppressions: Boolean,
    projectRoot: Path,
    errorSink: ErrorSink,
  ): DslTestPluginExpansionResult {
    val expandedDslTestPluginsByProduct = LinkedHashMap<String, List<TestPluginSpec>>()
    val dslTestPluginSuppressionUsages = ArrayList<SuppressionUsage>()
    val dslTestPluginDependencyChains = LinkedHashMap<PluginId, Map<ContentModuleName, List<ContentModuleName>>>()
    if (dslTestPluginsByProduct.isEmpty()) {
      return DslTestPluginExpansionResult(
        pluginsByProduct = expandedDslTestPluginsByProduct,
        suppressionUsages = dslTestPluginSuppressionUsages,
        dependencyChains = dslTestPluginDependencyChains,
      )
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // Phase 5: DSL-Defined Test Plugins
    // ═══════════════════════════════════════════════════════════════════════════════
    // PURPOSE: Create test plugins defined via TestPluginSpec DSL (not from plugin.xml).
    //          These are computed programmatically and added to both cache and graph.
    //
    // INVARIANT: After this phase:
    //   - DSL test plugins have NODE_PLUGIN vertices with NODE_FLAG_IS_DSL_DEFINED
    //   - Their content modules are linked via EDGE_CONTAINS_CONTENT_TEST
    //   - Plugin content is available in pluginContentCache
    //
    // DEPENDS ON: Phase 1 (for JPS dependency resolution during content computation)
    //
    // @see TestPluginSpec for DSL definition
    // @see computePluginContentFromDslSpec for content computation logic
    // ───────────────────────────────────────────────────────────────────────────────
    for (product in discovery.products) {
      val dslSpecs = dslTestPluginsByProduct[product.name].orEmpty()
      if (dslSpecs.isEmpty()) continue

      // Resolvable modules for DSL test plugins are derived from the product's module sets,
      // direct product content, and bundled production plugins (other test plugins excluded).
      val resolvableBaseModules = collectResolvableModules(graphView, product.name)

      val expandedSpecs = ArrayList<TestPluginSpec>(dslSpecs.size)
      for (dslSpec in dslSpecs) {
        val pluginModule = TargetName(dslSpec.pluginId.value)
        val declaredModules = collectDeclaredContentModules(dslSpec.spec)
        val additionalBundledModules = collectPluginContentModules(graphView, dslSpec.additionalBundledPluginTargetNames)
        val resolvableModules = LinkedHashSet<ContentModuleName>(resolvableBaseModules)
        resolvableModules.addAll(declaredModules)
        resolvableModules.addAll(additionalBundledModules)

        val dependencyChains = LinkedHashMap<ContentModuleName, List<ContentModuleName>>()
        val content = computePluginContentFromDslSpec(
          testPluginSpec = dslSpec,
          projectRoot = projectRoot,
          resolvableModules = resolvableModules,
          productName = product.name,
          pluginGraph = graphView,
          errorSink = errorSink,
          suppressionConfig = suppressionConfig,
          updateSuppressions = updateSuppressions,
          suppressionUsageSink = dslTestPluginSuppressionUsages,
          descriptorCache = descriptorCache,
          autoAddedModulesLoadingMode = config.dslTestPluginAutoAddLoadingMode,
          dependencyChainsSink = dependencyChains,
        )
        pluginContentCache.addDslTestPlugin(pluginModule, content)
        builder.addPluginWithContent(pluginModule, content, config.testFrameworkContentModules)
        builder.linkProductBundlesPlugin(product.name, pluginModule, isTest = true)
        expandedSpecs.add(expandTestPluginSpec(dslSpec, content, declaredModules, config.dslTestPluginAutoAddLoadingMode))
        if (dependencyChains.isNotEmpty()) {
          dslTestPluginDependencyChains.put(dslSpec.pluginId, dependencyChains)
        }
      }

      if (expandedSpecs.isNotEmpty()) {
        expandedDslTestPluginsByProduct[product.name] = expandedSpecs
      }
    }

    return DslTestPluginExpansionResult(
      pluginsByProduct = expandedDslTestPluginsByProduct,
      suppressionUsages = dslTestPluginSuppressionUsages,
      dependencyChains = dslTestPluginDependencyChains,
    )
  }

  private fun addJpsDependencies(
    builder: PluginGraphBuilder,
    outputProvider: ModuleOutputProvider,
    projectLibraryToModuleMap: Map<String, String>,
  ) {
    // ═══════════════════════════════════════════════════════════════════════════════
    // Phase 6: JPS Dependencies
    // ═══════════════════════════════════════════════════════════════════════════════
    // PURPOSE: Add EDGE_TARGET_DEPENDS_ON edges between JPS targets based on .iml dependencies.
    //          This enables dependency classification (embedded vs external).
    //
    // INVARIANT: After this phase:
    //   - All JPS module dependencies are represented as EDGE_TARGET_DEPENDS_ON edges
    //   - Edge scopes (COMPILE, TEST, RUNTIME, PROVIDED) are packed into EDGE_TARGET_DEPENDS_ON entries
    //   - New NODE_TARGET vertices created for dependency modules not yet in graph
    //
    // DEPENDS ON: Phases 1-5 (targets from plugins and module sets must exist)
    //
    // @see classifyTarget for how these edges are used in dependency classification
    // ───────────────────────────────────────────────────────────────────────────────
    builder.addJpsDependencies(outputProvider, projectLibraryToModuleMap)
  }

  private fun seedDslTestPluginTargets(
    builder: PluginGraphBuilder,
    dslTestPluginsByProduct: Map<String, List<TestPluginSpec>>,
  ) {
    if (dslTestPluginsByProduct.isEmpty()) {
      return
    }
    for (spec in dslTestPluginsByProduct.values.flatten()) {
      val declaredModules = collectDeclaredContentModules(spec.spec)
      for (moduleName in declaredModules) {
        if (moduleName.isSlashNotation()) continue
        val targetName = if (moduleName.isTestDescriptor()) {
          moduleName.baseModuleName().value
        }
        else {
          moduleName.value
        }
        builder.addTarget(TargetName(targetName))
      }
    }
  }

  private suspend fun registerReferencedPlugins(
    builder: PluginGraphBuilder,
    pluginContentCache: PluginContentCache,
    pluginInfos: MutableMap<TargetName, PluginContentInfo>,
  ) {
    // ═══════════════════════════════════════════════════════════════════════════════
    // Phase 7: Register Referenced Plugins
    // ═══════════════════════════════════════════════════════════════════════════════
    // PURPOSE: Discover and register plugins that are JPS dependencies but not
    //          explicitly bundled in any product. This ensures classifyTarget()
    //          can correctly identify plugin dependencies.
    //
    // INVARIANT: After this phase:
    //   - All JPS targets that have META-INF/plugin.xml are NODE_PLUGIN vertices
    //   - Non-bundled plugins have EDGE_MAIN_TARGET but no EDGE_BUNDLES
    //   - Discovered plugins have their content modules attached to the graph
    //
    // DEPENDS ON: Phase 6 (JPS dependencies must be added first to discover targets)
    //
    // @see classifyTarget for how plugin detection affects dependency classification
    // ───────────────────────────────────────────────────────────────────────────────
    val discoveredPluginInfos = builder.registerReferencedPlugins(pluginContentCache)
    if (discoveredPluginInfos.isNotEmpty()) {
      for ((pluginModule, info) in discoveredPluginInfos) {
        if (pluginModule !in pluginInfos) {
          pluginInfos[pluginModule] = info
        }
      }
    }
  }

  private fun addPluginDependencyEdges(
    builder: PluginGraphBuilder,
    pluginInfos: Map<TargetName, PluginContentInfo>,
  ) {
    // ═══════════════════════════════════════════════════════════════════════════════
    // Phase 8: Plugin Dependency Edges
    // ═══════════════════════════════════════════════════════════════════════════════
    // PURPOSE: Add plugin.xml dependency edges (plugin + content-module deps) to the graph.
    //          Unresolved plugin IDs become placeholder plugin nodes for later validation.
    //
    // INVARIANT: After this phase:
    //   - Plugin-to-plugin deps and plugin.xml module deps are represented in the graph
    //   - Optional legacy <depends> are stored with optional flag
    //
    // DEPENDS ON: Phase 7 (referenced plugins registered for accurate ID/alias resolution)
    // ───────────────────────────────────────────────────────────────────────────────
    builder.addPluginDependencyEdges(pluginInfos)
  }

  private fun seedPluginsForExtraction(
    discovery: DiscoveryResult,
    config: ModuleSetGenerationConfig,
    builder: PluginGraphBuilder,
    dslTestPluginIds: Set<PluginId>,
    dslTestPluginAdditionalBundles: Set<TargetName>,
    testPluginModuleNames: Set<TargetName>,
    extraPluginModules: Set<TargetName>,
  ) {
    // Compare by string value since TargetName (JPS module) and PluginId are different semantic types.
    val dslTestPluginIdStrings = dslTestPluginIds.mapTo(HashSet()) { it.value }
    fun addPlugin(target: TargetName) {
      if (target.value in dslTestPluginIdStrings) return
      builder.addPlugin(name = target, isTest = false)
    }

    for (product in discovery.products) {
      product.spec?.bundledPlugins?.forEach(::addPlugin)
    }
    for (nonBundled in config.nonBundledPlugins.values) {
      nonBundled.forEach(::addPlugin)
    }
    config.knownPlugins.forEach(::addPlugin)
    testPluginModuleNames.forEach(::addPlugin)
    dslTestPluginAdditionalBundles.forEach(::addPlugin)
    extraPluginModules.forEach(::addPlugin)
  }

  private fun collectSeededPluginTargets(graph: PluginGraph): List<TargetName> {
    val plugins = ArrayList<TargetName>()
    graph.query {
      plugins { plugin -> plugins.add(plugin.name()) }
    }
    return plugins
  }

  internal data class DiscoveredPluginDescriptors(
    val testPluginModules: Set<TargetName>,
    val pluginModules: Set<TargetName>,
  )

  internal fun discoverPluginDescriptorsFromSources(outputProvider: ModuleOutputProvider): DiscoveredPluginDescriptors {
    val modules = outputProvider.getAllModules()
    if (modules.isEmpty()) {
      return DiscoveredPluginDescriptors(emptySet(), emptySet())
    }

    val testPluginModules = LinkedHashSet<TargetName>()
    val pluginModules = LinkedHashSet<TargetName>()
    for (module in modules) {
      val prodPluginXml = findFileInModuleSources(module, PLUGIN_XML_RELATIVE_PATH, onlyProductionSources = true)
      val testPluginXml = if (prodPluginXml == null) {
        findFileInModuleSources(module, PLUGIN_XML_RELATIVE_PATH, onlyProductionSources = false)
      }
      else {
        null
      }
      if (testPluginXml != null) {
        testPluginModules.add(TargetName(module.name))
      }
      if (hasPluginContentYaml(module)) {
        pluginModules.add(TargetName(module.name))
      }
    }

    return DiscoveredPluginDescriptors(testPluginModules, pluginModules)
  }

  private fun hasPluginContentYaml(module: JpsModule): Boolean {
    for (url in module.contentRootsList.urls) {
      val rootPath = JpsPathUtil.urlToNioPath(url)
      if (Files.exists(rootPath.resolve("plugin-content.yaml"))) {
        return true
      }
    }
    return false
  }

  private fun collectDeclaredContentModules(spec: ProductModulesContentSpec): Set<ContentModuleName> {
    val contentData = buildContentBlocksAndChainMapping(spec, collectModuleSetAliases = false)
    return contentData.contentBlocks
      .flatMap { it.modules }
      .mapTo(LinkedHashSet()) { it.name }
  }

  private fun expandTestPluginSpec(
    spec: TestPluginSpec,
    content: PluginContentInfo,
    declaredModules: Set<ContentModuleName>,
    autoAddedModulesLoadingMode: ModuleLoadingRuleValue,
  ): TestPluginSpec {
    val autoAddedModules = content.contentModules
      .map { it.name }
      .filter { it !in declaredModules }

    if (autoAddedModules.isEmpty()) {
      return spec
    }

    val updatedSpec = ProductModulesContentSpec(
      productModuleAliases = spec.spec.productModuleAliases,
      vendor = spec.spec.vendor,
      deprecatedXmlIncludes = spec.spec.deprecatedXmlIncludes,
      moduleSets = spec.spec.moduleSets,
      additionalModules = spec.spec.additionalModules + autoAddedModules.map { ContentModule(it, autoAddedModulesLoadingMode) },
      bundledPlugins = spec.spec.bundledPlugins,
      allowedMissingDependencies = spec.spec.allowedMissingDependencies,
      compositionGraph = spec.spec.compositionGraph,
      metadata = spec.spec.metadata,
      testPlugins = spec.spec.testPlugins,
    )

    return spec.copy(spec = updatedSpec)
  }

  private suspend fun collectAliasesFromDeprecatedIncludes(
    spec: ProductModulesContentSpec,
    outputProvider: ModuleOutputProvider,
    isUltimateBuild: Boolean,
    includeAliasCache: AsyncCache<String, Set<PluginId>>,
    prefixFilter: (String) -> String?,
    skipXIncludePaths: Set<String>,
  ): Set<PluginId> {
    if (spec.deprecatedXmlIncludes.isEmpty()) {
      return emptySet()
    }

    val result = LinkedHashSet<PluginId>()
    for (include in spec.deprecatedXmlIncludes) {
      if (include.ultimateOnly && !isUltimateBuild) {
        continue
      }

      val moduleName = include.contentModuleName.value
      val cacheKey = "$moduleName:${include.resourcePath}"
      val aliases = includeAliasCache.getOrPut(cacheKey) {
        collectAliasesFromDeprecatedInclude(
          include = include,
          outputProvider = outputProvider,
          prefix = prefixFilter(moduleName),
          skipXIncludePaths = skipXIncludePaths,
        )
      }
      result.addAll(aliases)
    }

    return result
  }

  private suspend fun collectAliasesFromModuleDescriptors(
    moduleNames: Set<ContentModuleName>,
    descriptorCache: ModuleDescriptorCache,
    aliasCache: AsyncCache<ContentModuleName, Set<PluginId>>,
  ): Set<PluginId> {
    if (moduleNames.isEmpty()) {
      return emptySet()
    }

    val aliases = LinkedHashSet<PluginId>()
    for (moduleName in moduleNames) {
      val aliasSet = aliasCache.getOrPut(moduleName) {
        val descriptor = descriptorCache.getOrAnalyze(moduleName.value)
        val pluginAliases = descriptor?.pluginAliases ?: emptyList()
        pluginAliases.mapTo(LinkedHashSet()) { PluginId(it) }
      }
      aliases.addAll(aliasSet)
    }

    return aliases
  }

  private suspend fun collectAliasesFromDeprecatedInclude(
    include: DeprecatedXmlInclude,
    outputProvider: ModuleOutputProvider,
    prefix: String?,
    skipXIncludePaths: Set<String>,
  ): Set<PluginId> {
    val moduleName = include.contentModuleName.value
    val module = outputProvider.findModule(moduleName)
      ?: if (include.ultimateOnly) {
        error("Ultimate-only module '$moduleName' not found in Ultimate build - this is a configuration error (referenced in deprecated include for '${include.resourcePath}')")
      }
      else {
        error("Module '$moduleName' not found (referenced in deprecated include for '${include.resourcePath}')")
      }

    val initialData = resolveDeprecatedIncludeBytes(include, module, outputProvider)
    if (initialData == null) {
      if (include.optional) {
        debug("aliasGraph") { "deprecated include '${include.resourcePath}' not found in module '$moduleName' (optional)" }
        return emptySet()
      }
      error("Resource '${include.resourcePath}' not found in module '$moduleName' sources or libraries (referenced in deprecated include)")
    }

    return collectPluginAliasesFromXml(
      initialPath = include.resourcePath,
      initialData = initialData,
      outputProvider = outputProvider,
      module = module,
      prefix = prefix,
      skipXIncludePaths = skipXIncludePaths,
    )
  }

  private suspend fun resolveDeprecatedIncludeBytes(
    include: DeprecatedXmlInclude,
    module: JpsModule,
    outputProvider: ModuleOutputProvider,
  ): ByteArray? {
    val resourcePath = include.resourcePath
    findFileInModuleSources(module, resourcePath)?.let { return Files.readAllBytes(it) }
    findFileInModuleLibraryDependencies(module, resourcePath, outputProvider)?.let { return it }
    outputProvider.readFileContentFromModuleOutputAsync(module, resourcePath)?.let { return it }
    return null
  }

  private suspend fun collectPluginAliasesFromXml(
    initialPath: String,
    initialData: ByteArray,
    outputProvider: ModuleOutputProvider,
    module: JpsModule,
    prefix: String?,
    skipXIncludePaths: Set<String>,
  ): Set<PluginId> {
    val allAliases = LinkedHashSet<PluginId>()
    val processedPaths = HashSet<String>()

    var pending: List<Pair<String, ByteArray>> = listOf(initialPath to initialData)
    while (pending.isNotEmpty()) {
      val next = ArrayList<Pair<String, ByteArray>>()
      for ((path, data) in pending) {
        val parseResult = parseContentAndXIncludes(input = data, locationSource = null)
        for (alias in parseResult.pluginAliases) {
          allAliases.add(PluginId(alias))
        }
        for (xIncludePath in parseResult.xIncludePaths) {
          if (xIncludePath in skipXIncludePaths) {
            debug("aliasGraph") { "xi:include '$xIncludePath' skipped (in skipXIncludePaths, from '$path')" }
            continue
          }
          if (!processedPaths.add(xIncludePath)) {
            continue
          }
          val includeData = resolveXIncludeBytes(xIncludePath, module, outputProvider, prefix)
          if (includeData == null) {
            debug("aliasGraph") { "xi:include '$xIncludePath' not found (module=${module.name}, from '$path')" }
            continue
          }
          next.add(xIncludePath to includeData)
        }
      }
      pending = next
    }

    return allAliases
  }

  private suspend fun resolveXIncludeBytes(
    path: String,
    module: JpsModule,
    outputProvider: ModuleOutputProvider,
    prefix: String?,
  ): ByteArray? {
    findFileInModuleSources(module, path)?.let { return Files.readAllBytes(it) }
    findFileInModuleLibraryDependencies(module, path, outputProvider)?.let { return it }
    outputProvider.readFileContentFromModuleOutputAsync(module, path)?.let { return it }

    val processedModules = HashSet<String>()
    processedModules.add(module.name)

    findFileInModuleDependenciesRecursiveAsync(
      module = module,
      relativePath = path,
      provider = outputProvider,
      processedModules = processedModules,
      prefix = prefix,
    )?.let { return it }

    outputProvider.findFileInAnyModuleOutput(path, prefix, processedModules)?.let { return it }

    return null
  }
}
