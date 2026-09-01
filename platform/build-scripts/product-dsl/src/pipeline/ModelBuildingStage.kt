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
import com.intellij.platform.pluginGraph.contentName
import com.intellij.platform.pluginGraph.isSlashNotation
import com.intellij.platform.pluginGraph.isTestDescriptor
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue
import com.intellij.platform.pluginSystem.parser.impl.parseContentAndXIncludes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.PLUGIN_XML_RELATIVE_PATH
import org.jetbrains.intellij.build.findFileInModuleSources
import org.jetbrains.intellij.build.resolveDescriptor
import org.jetbrains.intellij.build.productLayout.ContentModule
import org.jetbrains.intellij.build.productLayout.DeprecatedXmlInclude
import org.jetbrains.intellij.build.productLayout.ProductModulesContentSpec
import org.jetbrains.intellij.build.productLayout.TestPluginSpec
import org.jetbrains.intellij.build.productLayout.appendDefaultProductPluginMetadata
import org.jetbrains.intellij.build.productLayout.buildContentBlocksAndChainMapping
import org.jetbrains.intellij.build.productLayout.buildProductContentXml
import org.jetbrains.intellij.build.productLayout.collectAndValidateAliases
import org.jetbrains.intellij.build.productLayout.config.SuppressionConfig
import org.jetbrains.intellij.build.productLayout.contentName
import org.jetbrains.intellij.build.productLayout.debug
import org.jetbrains.intellij.build.productLayout.dependency.ModuleDescriptorCache
import org.jetbrains.intellij.build.productLayout.dependency.PluginContentCache
import org.jetbrains.intellij.build.productLayout.deps.collectResolvableModules
import org.jetbrains.intellij.build.productLayout.discovery.DiscoveredProduct
import org.jetbrains.intellij.build.productLayout.discovery.ModuleSetGenerationConfig
import org.jetbrains.intellij.build.productLayout.discovery.PluginContentInfo
import org.jetbrains.intellij.build.productLayout.discovery.PluginXmlOverride
import org.jetbrains.intellij.build.productLayout.discovery.computePluginContentFromDslSpec
import org.jetbrains.intellij.build.productLayout.graph.PluginGraphBuilder
import org.jetbrains.intellij.build.productLayout.model.ErrorSink
import org.jetbrains.intellij.build.productLayout.model.error.DuplicateDslTestPluginIdError
import org.jetbrains.intellij.build.productLayout.stats.GenerationTiming
import org.jetbrains.intellij.build.productLayout.stats.SuppressionUsage
import org.jetbrains.intellij.build.productLayout.stats.recordGenerationTiming
import org.jetbrains.intellij.build.productLayout.traversal.collectPluginContentModules
import org.jetbrains.intellij.build.productLayout.traversal.collectProductModuleNames
import org.jetbrains.intellij.build.productLayout.util.AsyncCache
import org.jetbrains.intellij.build.productLayout.util.DeferredFileUpdater
import org.jetbrains.intellij.build.productLayout.util.GeneratedArtifactWritePolicy
import org.jetbrains.intellij.build.productLayout.util.resolveXIncludeBytes
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.util.JpsPathUtil
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import org.jetbrains.intellij.build.productLayout.ModuleSet
import org.jetbrains.intellij.build.productLayout.MODULE_SET_PREFIX

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
   * Each step is timed into [phaseTimings], because the whole stage reports as one bucket to the caller. Two rules hold
   * for a timing here.
   *
   * A span carries the name of the called function. A step also carries a `Phase N` label in a comment, and the labels
   * do not follow the execution order. The order of the labels is 1, 2, 3, 4, 4b, 6, 7, 5, 6, 7 and 8. A span named
   * after a label would mislabel itself. A step that runs twice gets a `#2` suffix, so a reader can compare the two
   * runs.
   *
   * The top level of this method is sequential, so a plain list is correct. This method also takes a [CoroutineScope].
   * A timing recorded inside a `scope.launch` would race, so never put one there.
   *
   * @param discovery Results from discovery stage
   * @param config Generation configuration
   * @param scope Coroutine scope for async operations
   * @param errorSink Sink for errors discovered during model building (e.g., xi:include resolution)
   * @param phaseTimings Collects one timing per step of this stage
   * @return Fully initialized generation model
   */
  suspend fun execute(
    discovery: DiscoveryResult,
    config: ModuleSetGenerationConfig,
    scope: CoroutineScope,
    updateSuppressions: Boolean,
    commitChanges: Boolean,
    errorSink: ErrorSink,
    phaseTimings: MutableList<GenerationTiming>,
  ): GenerationModel {
    val projectRoot = config.projectRoot
    val outputProvider = config.outputProvider
    val productPluginXmlOverrides = recordGenerationTiming("buildProductPluginXmlOverrides", phaseTimings) {
      buildProductPluginXmlOverrides(
        products = discovery.products,
        moduleSetsByLabel = discovery.moduleSetsByLabel,
        moduleSetSources = discovery.moduleSetSources,
        outputProvider = outputProvider,
        projectRoot = projectRoot,
        skipXIncludePaths = config.skipXIncludePaths,
        xIncludePrefixFilter = config.xIncludePrefixFilter,
      )
    }

    // Load suppression config from path (single source of truth)
    val suppressionConfig = recordGenerationTiming("SuppressionConfig.load", phaseTimings) {
      SuppressionConfig.load(config.suppressionConfigPath)
    }

    // Create file updater for deferred writes
    val fileUpdater = DeferredFileUpdater(projectRoot)
    val generationMode = when {
      updateSuppressions -> GenerationMode.UPDATE_SUPPRESSIONS
      !commitChanges -> GenerationMode.VALIDATE_ONLY
      else -> GenerationMode.NORMAL
    }
    val generatedArtifactWritePolicy = GeneratedArtifactWritePolicy(generationMode, fileUpdater)

    // Create xi:include cache (shared across plugin content extraction)
    val xIncludeCache = AsyncCache<String, ByteArray?>()

    // Create plugin content cache
    // ErrorSink is used to emit xi:include errors during plugin content extraction
    val pluginContentCache = PluginContentCache(
      outputProvider = outputProvider,
      xIncludeCache = xIncludeCache,
      skipXIncludePaths = config.skipXIncludePaths,
      xIncludePrefixFilter = config.xIncludePrefixFilter,
      pluginXmlOverrides = productPluginXmlOverrides,
      errorSink = errorSink,
    )
    // Build lookup for DSL-defined test plugins keyed by PluginId (semantically correct)
    // Note: PluginId is the XML plugin identifier, distinct from ModuleName (JPS module)
    // Includes both real products and test-only product specs (e.g. lambda-test fixtures).
    val dslTestPluginsByProduct = buildMap<String, List<TestPluginSpec>> {
      for (product in discovery.products) {
        val plugins = product.spec?.testPlugins?.takeIf { it.isNotEmpty() } ?: continue
        put(product.name, plugins)
      }
      for ((name, spec) in discovery.testProductSpecs) {
        val plugins = spec.testPlugins.takeIf { it.isNotEmpty() } ?: continue
        put(name, plugins)
      }
    }
    val dslTestPluginIdOwners = LinkedHashMap<PluginId, MutableList<String>>()
    for ((productName, testPlugins) in dslTestPluginsByProduct) {
      for (spec in testPlugins) {
        dslTestPluginIdOwners.getOrPut(spec.pluginId) { ArrayList() }.add(productName)
      }
    }
    for ((pluginId, owners) in dslTestPluginIdOwners) {
      if (owners.size <= 1) continue
      errorSink.emit(
        DuplicateDslTestPluginIdError(
          context = pluginId.value,
          pluginId = pluginId,
          productCounts = owners.groupingBy { it }.eachCount(),
        )
      )
    }
    val dslTestPlugins = dslTestPluginsByProduct.values.flatten()
    val dslTestPluginIds: Set<PluginId> = dslTestPlugins.mapTo(HashSet()) { it.pluginId }
    val dslTestPluginAdditionalBundles: Set<TargetName> = dslTestPlugins.asSequence()
      .flatMap { it.additionalBundledPluginTargetNames.asSequence() }
      .toSet()

    // Create descriptor cache
    val descriptorCache = ModuleDescriptorCache(outputProvider = outputProvider)

    // Build unified graph model for plugin/module/product relationships
    // Graph is the single source of truth - built DURING extraction
    val builder = PluginGraphBuilder(errorSink = errorSink)
    val pluginInfos = LinkedHashMap<TargetName, PluginContentInfo>()

    val extraPluginDescriptors = if (config.includeTestPluginDescriptorsFromSources) {
      val dslOwnedPluginXmlPaths = dslTestPluginsByProduct.values.asSequence()
        .flatten()
        .mapTo(HashSet()) { config.projectRoot.resolve(it.pluginXmlPath).normalize() }
      // The span covers `readDevDistContentPluginPopulation` too, because the call reads a file.
      // `config.includeTestPluginDescriptorsFromSources` guards the step, so no span means the flag was off.
      recordGenerationTiming("discoverPluginDescriptorsFromSources", phaseTimings) {
        discoverPluginDescriptorsFromSources(
          outputProvider = outputProvider,
          testFrameworkContentModules = config.testFrameworkContentModules,
          dslOwnedPluginXmlPaths = dslOwnedPluginXmlPaths,
          contentPluginPopulation = readDevDistContentPluginPopulation(projectRoot),
        )
      }
    }
    else {
      DiscoveredPluginDescriptors(emptySet(), emptySet())
    }
    val testPluginModuleNames = config.testPluginsByProduct.values.flatten().toHashSet()
    testPluginModuleNames.addAll(extraPluginDescriptors.testPluginModules)
    recordGenerationTiming("seedPluginsForExtraction", phaseTimings) {
      seedPluginsForExtraction(
        discovery = discovery,
        config = config,
        builder = builder,
        dslTestPluginIds = dslTestPluginIds,
        dslTestPluginAdditionalBundles = dslTestPluginAdditionalBundles,
        testPluginModuleNames = testPluginModuleNames,
        extraPluginModules = extraPluginDescriptors.pluginModules,
      )
    }
    // The graph view is built three times here, and frozen once. Each build gets its own span, because the cost of a
    // rebuild is not known.
    val seededGraphView = recordGenerationTiming("builder.build", phaseTimings) { builder.build() }
    val pluginsToExtract = recordGenerationTiming("collectSeededPluginTargets", phaseTimings) {
      collectSeededPluginTargets(seededGraphView)
    }
    recordGenerationTiming("extractPlugins", phaseTimings) {
      extractPlugins(
        pluginTargets = pluginsToExtract,
        pluginContentCache = pluginContentCache,
        builder = builder,
        pluginInfos = pluginInfos,
        testPluginModuleNames = testPluginModuleNames,
        testFrameworkContentModules = config.testFrameworkContentModules,
      )
    }

    val includeAliasCache = AsyncCache<String, Set<PluginId>>()
    val moduleDescriptorAliasCache = AsyncCache<ContentModuleName, Set<PluginId>>()
    recordGenerationTiming("linkProductsAndBundledPlugins", phaseTimings) { linkProductsAndBundledPlugins(discovery, builder) }
    recordGenerationTiming("linkTestPluginsByProduct", phaseTimings) { linkTestPluginsByProduct(config, builder) }
    recordGenerationTiming("addModuleSets", phaseTimings) { addModuleSets(discovery, builder) }
    val baseGraphView = recordGenerationTiming("builder.build #2", phaseTimings) { builder.build() }
    recordGenerationTiming("linkProductAliases", phaseTimings) {
      linkProductAliases(
        discovery = discovery,
        config = config,
        builder = builder,
        graphView = baseGraphView,
        outputProvider = outputProvider,
        descriptorCache = descriptorCache,
        includeAliasCache = includeAliasCache,
        moduleDescriptorAliasCache = moduleDescriptorAliasCache,
        pluginInfos = pluginInfos,
      )
    }
    recordGenerationTiming("seedDslTestPluginTargets", phaseTimings) { seedDslTestPluginTargets(builder, dslTestPluginsByProduct) }
    recordGenerationTiming("addJpsDependencies", phaseTimings) {
      addJpsDependencies(builder, outputProvider, config.projectLibraryToModuleMap)
    }
    recordGenerationTiming("registerReferencedPlugins", phaseTimings) {
      registerReferencedPlugins(builder, pluginContentCache, pluginInfos)
    }
    recordGenerationTiming("builder.markDescriptorModules", phaseTimings) { builder.markDescriptorModules(descriptorCache) }
    val graphWithJpsDeps = recordGenerationTiming("builder.build #3", phaseTimings) { builder.build() }

    val dslTestPluginExpansion = recordGenerationTiming("expandDslTestPlugins", phaseTimings) {
      expandDslTestPlugins(
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
    }
    recordGenerationTiming("addJpsDependencies #2", phaseTimings) {
      addJpsDependencies(builder, outputProvider, config.projectLibraryToModuleMap)
    }
    recordGenerationTiming("registerReferencedPlugins #2", phaseTimings) {
      registerReferencedPlugins(builder, pluginContentCache, pluginInfos)
    }
    recordGenerationTiming("builder.markDescriptorModules #2", phaseTimings) { builder.markDescriptorModules(descriptorCache) }
    recordGenerationTiming("addPluginDependencyEdges", phaseTimings) { addPluginDependencyEdges(builder, pluginInfos) }

    val pluginGraph = recordGenerationTiming("builder.buildFrozen", phaseTimings) { builder.buildFrozen() }

    // Build per-product allowedMissingDependencies map — includes both real products and test product specs
    val productAllowedMissing = (
      discovery.products.mapNotNull { d -> d.spec?.allowedMissingDependencies?.let { d.name to it } } +
      discovery.testProductSpecs.mapNotNull { (name, spec) -> spec.allowedMissingDependencies.takeIf { it.isNotEmpty() }?.let { name to it } }
    )
      .toMap()

    return GenerationModel(
      discovery = discovery,
      config = config,
      projectRoot = projectRoot,
      outputProvider = outputProvider,
      descriptorCache = descriptorCache,
      pluginContentCache = pluginContentCache,
      fileUpdater = fileUpdater,
      generatedArtifactWritePolicy = generatedArtifactWritePolicy,
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

  /**
   * Precomputes in-memory product plugin.xml content from DSL specs.
   *
   * This allows extraction/validation to use canonical generated descriptors for discovered
   * product plugins even when on-disk product plugin.xml files are stale.
   */
  internal suspend fun buildProductPluginXmlOverrides(
    products: List<DiscoveredProduct>,
    moduleSetsByLabel: Map<String, List<ModuleSet>> = emptyMap(),
    moduleSetSources: Map<String, Pair<Any, Path>> = emptyMap(),
    outputProvider: ModuleOutputProvider,
    projectRoot: Path,
    skipXIncludePaths: Set<String>,
    xIncludePrefixFilter: (String) -> String?,
  ): Map<TargetName, PluginXmlOverride> {
    // Only the descriptors the products name are wanted, so only a module that could hold one is worth a stat.
    // `findFileInModuleSources` resolves the relative path under a source root, so a module answers for a path only
    // when one of its production roots is a parent of that path. Asking every module cost a stat per root of all
    // 7089 of them, to learn about at most 28 paths.
    val wantedPluginXmlPaths = products.mapNotNullTo(HashSet()) { product ->
      product.pluginXmlPath?.let { projectRoot.resolve(it).normalize() }
    }

    val sweepStartNano = System.nanoTime()
    var scannedModuleCount = 0
    val moduleByPluginXmlPath = LinkedHashMap<Path, TargetName>()
    val productionSourceRootsByModule = LinkedHashMap<TargetName, List<Path>>()
    for (module in outputProvider.getAllModules()) {
      val moduleName = TargetName(module.name)
      val productionSourceRoots = module.sourceRoots
        .asSequence()
        .filter { root ->
          root.rootType == JavaSourceRootType.SOURCE || root.rootType == JavaResourceRootType.RESOURCE
        }
        .map { root -> JpsPathUtil.urlToNioPath(root.url).normalize() }
        .toList()
      if (productionSourceRoots.isNotEmpty()) {
        productionSourceRootsByModule[moduleName] = productionSourceRoots
      }

      if (productionSourceRoots.none { root -> wantedPluginXmlPaths.any { it.startsWith(root) } }) {
        continue
      }

      scannedModuleCount++
      val pluginXmlPath = findFileInModuleSources(module = module, relativePath = PLUGIN_XML_RELATIVE_PATH, onlyProductionSources = true)
      if (pluginXmlPath != null) {
        moduleByPluginXmlPath[pluginXmlPath.normalize()] = moduleName
      }
    }

    val sweepMs = (System.nanoTime() - sweepStartNano) / 1_000_000
    val productLoopStartNano = System.nanoTime()
    var examinedProductCount = 0
    var checkedProductCount = 0
    var renderedProductCount = 0
    var descriptorCheckNano = 0L
    var renderNano = 0L
    val probeStats = XIncludeProbeStats()

    // The model states the module that owns an include, so the search does not have to look for it. Without the
    // owner the search reaches a generated module-set descriptor only through the last resort, which opens an output
    // jar for every module in the project. An include that the model does not name keeps that search.
    val declaredIncludeOwners = HashMap<String, JpsModule>()
    for (product in products) {
      for (include in product.spec?.deprecatedXmlIncludes.orEmpty()) {
        val owner = outputProvider.findModule(include.contentModuleName.value) ?: continue
        declaredIncludeOwners.putIfAbsent(include.resourcePath.removePrefix("/"), owner)
      }
    }
    // A module set writes its descriptor either into the module it names, or into the resource root that its label's
    // output directory belongs to. `productionSourceRootsByModule` above already holds every root, so the second case
    // needs no new I/O.
    for ((label, moduleSets) in moduleSetsByLabel) {
      val defaultOutputDir = moduleSetSources.get(label)?.second ?: continue
      val labelOwner = productionSourceRootsByModule.entries
        .firstOrNull { (_, roots) -> roots.any { defaultOutputDir.startsWith(it) } }
        ?.let { outputProvider.findModule(it.key.value) }
      for (moduleSet in moduleSets) {
        val owner = moduleSet.outputModule?.let { outputProvider.findModule(it.value) } ?: labelOwner ?: continue
        declaredIncludeOwners.putIfAbsent("META-INF/$MODULE_SET_PREFIX${moduleSet.name}.xml", owner)
      }
    }
    // One resolution per path for the whole run. Twenty-nine paths answered 121 requests before this cache.
    val resolvedIncludes = HashMap<String, ByteArray?>()

    val result = LinkedHashMap<TargetName, PluginXmlOverride>()
    val ownerByModule = LinkedHashMap<TargetName, String>()
    for (product in products) {
      examinedProductCount++
      val spec = product.spec ?: continue
      val relativePluginXmlPath = product.pluginXmlPath ?: continue
      val pluginXmlPath = projectRoot.resolve(relativePluginXmlPath).normalize()
      val pluginModule = resolveProductPluginModule(
        productName = product.name,
        pluginXmlPath = pluginXmlPath,
        relativePluginXmlPath = relativePluginXmlPath,
        moduleByPluginXmlPath = moduleByPluginXmlPath,
        productionSourceRootsByModule = productionSourceRootsByModule,
      )

      // Keep non-IntelliJ product plugin descriptors untouched for now: some products
      // (e.g. fleet.*) intentionally maintain handcrafted descriptors that may differ from DSL output.
      if (!pluginModule.value.startsWith("intellij.")) {
        continue
      }

      val module = outputProvider.findModule(pluginModule.value)
                   ?: error("Cannot find module '${pluginModule.value}' for product plugin.xml '$relativePluginXmlPath'")
      if (Files.notExists(pluginXmlPath)) {
        error("Product '${product.name}' plugin.xml '$relativePluginXmlPath' does not exist at '$pluginXmlPath'")
      }

      checkedProductCount++
      val xIncludePrefix = xIncludePrefixFilter(pluginModule.value)
      val descriptorCheckStartNano = System.nanoTime()
      val pluginXmlData = withContext(Dispatchers.IO) { Files.readAllBytes(pluginXmlPath) }
      val problems = findDescriptorProblems(
        pluginXmlData = pluginXmlData,
        module = module,
        outputProvider = outputProvider,
        prefix = xIncludePrefix,
        skipXIncludePaths = skipXIncludePaths,
        declaredIncludeOwners = declaredIncludeOwners,
        resolvedIncludes = resolvedIncludes,
        stats = probeStats,
      )
      val unresolvedXInclude = problems.unresolvedXInclude
      val missingBackingContentModule = problems.missingBackingContentModule
      descriptorCheckNano += System.nanoTime() - descriptorCheckStartNano
      if (unresolvedXInclude == null && missingBackingContentModule == null) {
        continue
      }

      renderedProductCount++
      val renderStartNano = System.nanoTime()
      val generatedPluginXml = buildProductContentXml(
        spec = spec,
        outputProvider = outputProvider,
        inlineXmlIncludes = false,
        inlineModuleSets = false,
        metadataBuilder = { sb ->
          appendDefaultProductPluginMetadata(sb = sb, spec = spec)
        },
      ).xml
      val unresolvedXIncludeInGenerated = findDescriptorProblems(
        pluginXmlData = generatedPluginXml.toByteArray(),
        module = module,
        outputProvider = outputProvider,
        prefix = xIncludePrefix,
        skipXIncludePaths = skipXIncludePaths,
        declaredIncludeOwners = declaredIncludeOwners,
        resolvedIncludes = resolvedIncludes,
        stats = probeStats,
      ).unresolvedXInclude
      renderNano += System.nanoTime() - renderStartNano
      if (unresolvedXIncludeInGenerated != null) {
        debug("productPluginOverride") {
          "skipping generated override for ${pluginModule.value}: unresolved xi:include '$unresolvedXIncludeInGenerated' remains in generated descriptor"
        }
        continue
      }
      val reason = unresolvedXInclude?.let {
        "unresolved xi:include '$it' in source plugin.xml"
      }
                   ?: "content module '$missingBackingContentModule' has no backing JPS module in source plugin.xml"
      debug("productPluginOverride") {
        "using generated override for ${pluginModule.value} due to $reason"
      }

      val override = PluginXmlOverride(
        pluginXmlPath = pluginXmlPath,
        pluginXmlContent = generatedPluginXml,
      )
      val existing = result[pluginModule]
      if (existing != null && existing.pluginXmlContent != override.pluginXmlContent) {
        val owner = ownerByModule[pluginModule] ?: "<unknown>"
        error(
          "Conflicting generated product plugin.xml content for module '${pluginModule.value}': products '$owner' and '${product.name}'"
        )
      }
      if (existing == null) {
        result[pluginModule] = override
        ownerByModule[pluginModule] = product.name
      }
    }

    debug("timings") {
      val productLoopMs = (System.nanoTime() - productLoopStartNano) / 1_000_000
      "buildProductPluginXmlOverrides: module sweep $sweepMs ms over $scannedModuleCount statted modules, " +
      "product loop $productLoopMs ms over $examinedProductCount products " +
      "(descriptor check ${descriptorCheckNano / 1_000_000} ms over $checkedProductCount, " +
      "render ${renderNano / 1_000_000} ms over $renderedProductCount), " +
      "${probeStats.parseCalls} parses, ${probeStats.resolveRequests} include requests met by " +
      "${probeStats.resolveMisses} resolutions costing ${probeStats.resolveNano / 1_000_000} ms, " +
      "${result.size} overrides"
    }
    return result
  }

  private fun resolveProductPluginModule(
    productName: String,
    pluginXmlPath: Path,
    relativePluginXmlPath: String,
    moduleByPluginXmlPath: Map<Path, TargetName>,
    productionSourceRootsByModule: Map<TargetName, List<Path>>,
  ): TargetName {
    moduleByPluginXmlPath[pluginXmlPath]?.let { return it }

    val candidates = productionSourceRootsByModule
      .asSequence()
      .filter { (_, roots) -> roots.any { root -> pluginXmlPath.startsWith(root) } }
      .map { (module, _) -> module }
      .toList()

    if (candidates.size == 1) {
      return candidates.single()
    }

    if (candidates.size > 1) {
      error(
        "Cannot uniquely map product '$productName' plugin.xml '$relativePluginXmlPath' to a module; candidates=" +
        candidates.joinToString { it.value }
      )
    }

    error("Cannot map product '$productName' plugin.xml '$relativePluginXmlPath' to a module with production sources")
  }

  /** Counts what one run of the descriptor pre-check spends, for the `timings` debug tag. */
  internal class XIncludeProbeStats {
    @JvmField var resolveRequests: Int = 0
    @JvmField var resolveMisses: Int = 0
    @JvmField var parseCalls: Int = 0
    @JvmField var resolveNano: Long = 0
  }

  /** What a product's on-disk descriptor gets wrong, or nothing at all when it is healthy. */
  private class DescriptorProblems(
    @JvmField val unresolvedXInclude: String?,
    @JvmField val missingBackingContentModule: String?,
  )

  /**
   * Walks the `xi:include` closure of [pluginXmlData] once, and reports both faults that an override repairs.
   *
   * One walk answers both questions. The two faults come from the same parse of the same file, and resolving an
   * include is what the walk spends its time on. Two walks resolved every include twice.
   *
   * An unresolved include wins over a missing content module. A file that does not resolve hides what it declares,
   * so the include is the fault to report and the walk stops there.
   */
  private suspend fun findDescriptorProblems(
    pluginXmlData: ByteArray,
    module: JpsModule,
    outputProvider: ModuleOutputProvider,
    prefix: String?,
    skipXIncludePaths: Set<String>,
    declaredIncludeOwners: Map<String, JpsModule>,
    resolvedIncludes: MutableMap<String, ByteArray?>,
    stats: XIncludeProbeStats? = null,
  ): DescriptorProblems {
    val processedPaths = HashSet<String>()
    var pending: List<Pair<String, ByteArray>> = listOf(PLUGIN_XML_RELATIVE_PATH to pluginXmlData)
    var missingBackingContentModule: String? = null

    while (pending.isNotEmpty()) {
      val next = ArrayList<Pair<String, ByteArray>>()
      for ((_, data) in pending) {
        if (stats != null) stats.parseCalls++
        val parseResult = parseContentAndXIncludes(input = data, locationSource = null)

        if (missingBackingContentModule == null) {
          for (contentModule in parseResult.contentModules) {
            val moduleName = ContentModuleName(contentModule.name)
            if (moduleName.isSlashNotation()) {
              continue
            }

            val expectedTarget = moduleName.baseModuleName().value
            if (outputProvider.findModule(expectedTarget) == null) {
              missingBackingContentModule = moduleName.value
              break
            }
          }
        }

        for (xIncludePath in parseResult.xIncludePaths) {
          if (xIncludePath in skipXIncludePaths) continue
          if (!processedPaths.add(xIncludePath)) continue

          if (stats != null) stats.resolveRequests++
          val resolveStartNano = System.nanoTime()
          val includeData = if (resolvedIncludes.containsKey(xIncludePath)) {
            resolvedIncludes.get(xIncludePath)
          }
          else {
            if (stats != null) stats.resolveMisses++
            resolveXIncludeBytes(
              path = xIncludePath,
              module = module,
              outputProvider = outputProvider,
              prefix = prefix,
              declaredOwner = declaredIncludeOwners.get(xIncludePath.removePrefix("/")),
            ).also { resolvedIncludes.put(xIncludePath, it) }
          }
          if (stats != null) stats.resolveNano += System.nanoTime() - resolveStartNano
          if (includeData == null) {
            return DescriptorProblems(unresolvedXInclude = xIncludePath, missingBackingContentModule = null)
          }
          next.add(xIncludePath to includeData)
        }
      }
      pending = next
    }

    return DescriptorProblems(unresolvedXInclude = null, missingBackingContentModule = missingBackingContentModule)
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

      for (pluginModule in product.bundledModuleSetPluginModules) {
        builder.linkProductBundlesPlugin(productName = product.name, pluginName = pluginModule, isTest = false)
      }

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
      for (module in spec.additionalModules) {
        builder.linkProductContainsContent(product.name, module.moduleId, module.loading)
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
    fun linkProductBundlesAlias(productName: String, alias: PluginId) {
      val productId = builder.addProduct(productName)
      val aliasNodeId = builder.addAliasPlugin(alias)
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
          aliasIds.addAll(
            collectAliasesFromDeprecatedIncludes(
              spec,
              outputProvider,
              includeAliasCache,
              config.xIncludePrefixFilter,
              config.skipXIncludePaths,
            )
          )

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
                val pluginModuleNames = info.contentModules.mapTo(LinkedHashSet()) { it.moduleId.contentName() }
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

    // Also expand test plugins from testProductSpecs (e.g. lambda-test fixtures).
    // For test product specs, keep wrapper specs small but disable content auto-add by marking all
    // graph modules as "resolvable". The dependency planner still generates <dependencies>.
    // Content auto-add would otherwise pull in the transitive closure of every declared module,
    // including non-bundled test-infrastructure modules (like intellij.tools.ide.starter) that own
    // native libraries (pty4j) conflicting with what the core IDE already extracted at runtime.
    val allGraphContentModules: Set<ContentModuleName> by lazy {
      val result = LinkedHashSet<ContentModuleName>()
      graphView.query { contentModules { result.add(it.contentName()) } }
      result
    }

    for ((specName, _) in discovery.testProductSpecs) {
      val dslSpecs = dslTestPluginsByProduct[specName].orEmpty()
      if (dslSpecs.isEmpty()) continue

      // Register as a product node so validators can apply per-product allowed-missing rules.
      builder.addProduct(specName)

      val expandedSpecs = ArrayList<TestPluginSpec>(dslSpecs.size)
      for (dslSpec in dslSpecs) {
        val pluginModule = TargetName(dslSpec.pluginId.value)
        val declaredModules = collectDeclaredContentModules(dslSpec.spec)
        val resolvableModules = LinkedHashSet<ContentModuleName>(allGraphContentModules)
        resolvableModules.addAll(declaredModules)

        val dependencyChains = LinkedHashMap<ContentModuleName, List<ContentModuleName>>()
        val content = computePluginContentFromDslSpec(
          testPluginSpec = dslSpec,
          projectRoot = projectRoot,
          resolvableModules = resolvableModules,
          productName = specName,
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
        // Link to the test product so the validator's per-product allowed-missing check applies.
        builder.linkProductBundlesPlugin(specName, pluginModule, isTest = true)
        expandedSpecs.add(expandTestPluginSpec(dslSpec, content, declaredModules, config.dslTestPluginAutoAddLoadingMode))
        if (dependencyChains.isNotEmpty()) {
          dslTestPluginDependencyChains.put(dslSpec.pluginId, dependencyChains)
        }
      }

      if (expandedSpecs.isNotEmpty()) {
        expandedDslTestPluginsByProduct[specName] = expandedSpecs
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
    fun addPlugin(target: TargetName, pluginId: PluginId? = null) {
      if (target.value in dslTestPluginIdStrings) return
      builder.addPlugin(name = target, isTest = false, pluginId = pluginId)
    }

    for (product in discovery.products) {
      product.bundledModuleSetPluginModules.forEach(::addPlugin)
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

  /**
   * The plugin main modules to seed into the graph, read off the module sources.
   *
   * @param contentPluginPopulation the plugin main modules the dev distribution states content for.
   * [readDevDistContentPluginPopulation] reads it. A name this project does not hold is a name nothing matches.
   */
  internal fun discoverPluginDescriptorsFromSources(
    outputProvider: ModuleOutputProvider,
    testFrameworkContentModules: Set<ContentModuleName> = emptySet(),
    dslOwnedPluginXmlPaths: Set<Path> = emptySet(),
    contentPluginPopulation: Set<String> = emptySet(),
  ): DiscoveredPluginDescriptors {
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
      if (prodPluginXml != null && prodPluginXml in dslOwnedPluginXmlPaths) {
        // A DSL `testPlugin {}` already owns this descriptor under its own plugin id; seeding the JPS module as well
        // would put the same plugin.xml in the graph twice, under two different plugin nodes.
        continue
      }
      if (testPluginXml != null) {
        testPluginModules.add(TargetName(module.name))
      }
      else if (prodPluginXml != null && declaresTestFrameworkContent(prodPluginXml, testFrameworkContentModules)) {
        // Test plugins that keep plugin.xml in production resources (the rdct and ReSharper ones, for instance) are
        // invisible to every list the generator is configured with, so without this they get neither auto-add nor
        // validation and a content module conversion breaks them silently (IJPL-252475).
        testPluginModules.add(TargetName(module.name))
      }
      if (module.name in contentPluginPopulation) {
        pluginModules.add(TargetName(module.name))
      }
    }

    return DiscoveredPluginDescriptors(testPluginModules, pluginModules)
  }

  /**
   * Cheap text probe: a descriptor declaring one of the test-framework marker modules as content is a test plugin,
   * the same criterion [org.jetbrains.intellij.build.productLayout.validator.rule.isTestPlugin] applies once the plugin
   * is in the graph. Parsing every plugin.xml in the project just to seed extraction would be far more expensive.
   */
  private fun declaresTestFrameworkContent(pluginXml: Path, testFrameworkContentModules: Set<ContentModuleName>): Boolean {
    if (testFrameworkContentModules.isEmpty()) {
      return false
    }
    val content = try {
      Files.readString(pluginXml)
    }
    catch (_: IOException) {
      return false
    }
    return testFrameworkContentModules.any { content.contains("\"${it.value}\"") }
  }

  private fun collectDeclaredContentModules(spec: ProductModulesContentSpec): Set<ContentModuleName> {
    val contentData = buildContentBlocksAndChainMapping(spec, collectModuleSetAliases = false)
    return contentData.contentBlocks
      .flatMap { it.modules }
      .mapTo(LinkedHashSet()) { it.contentName() }
  }

  private fun expandTestPluginSpec(
    spec: TestPluginSpec,
    content: PluginContentInfo,
    declaredModules: Set<ContentModuleName>,
    autoAddedModulesLoadingMode: ModuleLoadingRuleValue,
  ): TestPluginSpec {
    val autoAddedModules = content.contentModules
      .filter { it.moduleId.contentName() !in declaredModules }

    if (autoAddedModules.isEmpty()) {
      return spec
    }

    val updatedSpec = ProductModulesContentSpec(
      productModuleAliases = spec.spec.productModuleAliases,
      vendor = spec.spec.vendor,
      deprecatedXmlIncludes = spec.spec.deprecatedXmlIncludes,
      moduleSets = spec.spec.moduleSets,
      additionalModules = spec.spec.additionalModules + autoAddedModules.map {
        ContentModule(moduleId = it.moduleId, loading = autoAddedModulesLoadingMode)
      },
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
    includeAliasCache: AsyncCache<String, Set<PluginId>>,
    prefixFilter: (String) -> String?,
    skipXIncludePaths: Set<String>,
  ): Set<PluginId> {
    if (spec.deprecatedXmlIncludes.isEmpty()) {
      return emptySet()
    }

    val result = LinkedHashSet<PluginId>()
    for (include in spec.deprecatedXmlIncludes) {
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
                 ?: error("Module '$moduleName' not found (referenced in deprecated include for '${include.resourcePath}')")


    // The model names the owner of a `deprecatedInclude`, so the search stays inside that module and its libraries.
    val initialData = resolveDescriptor(
      module = module,
      path = include.resourcePath,
      outputProvider = outputProvider,
      walk = null,
      searchAnyModuleOutput = false,
    )
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
}

/**
 * The population file, relative to the project root.
 *
 * Under `community/build/`, so a community-only checkout reads the same file. A line naming a plugin that checkout does
 * not have is a line it never matches.
 */
const val DEV_DIST_CONTENT_PLUGIN_POPULATION_PATH: String = "community/build/dev_dist_plugin_content_population.txt"

/**
 * The plugin main modules the dev distribution states content for, one name per line.
 *
 * This answers "is this module a shipped plugin's main module", which the graph needs to seed a plugin the product specs
 * do not name. The earlier signal was a `plugin-content.yaml` beside the module, and that file goes away: it enumerated
 * what a distribution build really packed, and the dev distribution now derives that from the project model. The
 * population is the one part of it the derivation cannot answer, so it stays as a plain checked-in list. Read
 * `build/decisions/0007-the-descriptor-leaf-follows-the-content-leaf.md` for why the hand-off is a text file.
 *
 * A `#` line is a comment. An empty result on an absent file, the same fail-open the converter's reader takes: a
 * throwaway project holds no such file, and this enrichment runs in the analysis-only flow alone.
 *
 * Public, because the dev-distribution plan generator reads the same file to decide whether a plugin has a content
 * target to point at. That generator is in the main repository and it depends on this module, so the two share one
 * spelling of the path and one parse. The JPS-to-Bazel converter is the third reader and it cannot share: it is a
 * standalone Bazel module that takes the platform as published artifacts.
 */
fun readDevDistContentPluginPopulation(projectRoot: Path): Set<String> {
  val file = projectRoot.resolve(DEV_DIST_CONTENT_PLUGIN_POPULATION_PATH)
  if (Files.notExists(file)) {
    return emptySet()
  }
  val result = LinkedHashSet<String>()
  for (raw in Files.readAllLines(file)) {
    val line = raw.trim()
    if (line.isNotEmpty() && !line.startsWith('#')) {
      result.add(line)
    }
  }
  return result
}
