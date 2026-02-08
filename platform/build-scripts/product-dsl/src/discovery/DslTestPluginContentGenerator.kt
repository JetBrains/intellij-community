// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment", "GrazieInspection", "GrazieStyle")

package org.jetbrains.intellij.build.productLayout.discovery

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.EDGE_BUNDLES
import com.intellij.platform.pluginGraph.NODE_PLUGIN
import com.intellij.platform.pluginGraph.PluginGraph
import com.intellij.platform.pluginGraph.PluginId
import com.intellij.platform.pluginGraph.TargetDependencyScope
import com.intellij.platform.pluginGraph.baseModuleName
import com.intellij.platform.pluginGraph.containsEdge
import com.intellij.platform.pluginGraph.isSlashNotation
import com.intellij.platform.pluginGraph.isTestDescriptor
import com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRuleValue
import org.jetbrains.intellij.build.productLayout.LIB_MODULE_PREFIX
import org.jetbrains.intellij.build.productLayout.TestPluginSpec
import org.jetbrains.intellij.build.productLayout.buildContentBlocksAndChainMapping
import org.jetbrains.intellij.build.productLayout.config.SuppressionConfig
import org.jetbrains.intellij.build.productLayout.debug
import org.jetbrains.intellij.build.productLayout.dependency.ModuleDescriptorCache
import org.jetbrains.intellij.build.productLayout.deps.DependencyResolutionContext
import org.jetbrains.intellij.build.productLayout.model.ErrorSink
import org.jetbrains.intellij.build.productLayout.stats.SuppressionType
import org.jetbrains.intellij.build.productLayout.stats.SuppressionUsage
import org.jetbrains.intellij.build.productLayout.traversal.OwningPlugin
import org.jetbrains.intellij.build.productLayout.traversal.collectPluginContentModules
import java.nio.file.Path

private val TEST_PLUGIN_AUTO_ADD_EXCLUDED_MODULES = setOf(
  ContentModuleName("intellij.platform.commercial.verifier"), // injected; forbidden as plugin content
)

/**
 * Computes plugin content from DSL spec instead of reading from disk.
 * Used for DSL-defined test plugins to avoid reading stale XML before generation.
 *
 * Auto-adds JPS dependencies that have module descriptors but aren't explicitly declared,
 * are not resolvable via module sets, bundled production plugin content, or testPlugin additionalBundledPluginTargetNames,
 * and are not already provided by bundled production plugins for [productName] (other test plugins excluded).
 * This enables minimal DSL specifications - only specify module sets, and individual
 * dependencies with descriptors are automatically discovered and added.
 *
 * For test descriptor modules (`._test`), explicit descriptor `<module>` deps are also traversed
 * so test-only content stays self-contained.
 *
 * Library dependencies are represented in the graph via project library mapping during graph building.
 *
 * See [docs/test-plugins.md](../../docs/test-plugins.md) for details on auto-add behavior.
 */
internal suspend fun computePluginContentFromDslSpec(
  testPluginSpec: TestPluginSpec,
  projectRoot: Path,
  resolvableModules: Set<ContentModuleName>,
  productName: String,
  pluginGraph: PluginGraph,
  errorSink: ErrorSink,
  suppressionConfig: SuppressionConfig = SuppressionConfig(),
  updateSuppressions: Boolean = false,
  suppressionUsageSink: MutableList<SuppressionUsage>? = null,
  // Nullable to allow graph-only usage in tests or callers that don't want disk I/O.
  // When null, test-descriptor module deps are not loaded from disk.
  descriptorCache: ModuleDescriptorCache? = null,
  autoAddedModulesLoadingMode: ModuleLoadingRuleValue = ModuleLoadingRuleValue.OPTIONAL,
  dependencyChainsSink: MutableMap<ContentModuleName, List<ContentModuleName>>? = null,
): PluginContentInfo {
  val contentData = buildContentBlocksAndChainMapping(testPluginSpec.spec, collectModuleSetAliases = false)
  val resolutionContext = DependencyResolutionContext(pluginGraph)
  val allModules = contentData.contentBlocks.flatMap { it.modules }
  val strictModules = allModules
    .asSequence()
    .filter { module ->
      module.loading == ModuleLoadingRuleValue.REQUIRED || module.loading == ModuleLoadingRuleValue.EMBEDDED
    }
    .mapTo(HashSet()) { it.name }

  // Build set of explicitly declared module names for quick lookup
  val declaredContentModuleNames = allModules.mapTo(HashSet()) { it.name }
  require(pluginGraph.descriptorFlagsComplete) {
    "PluginGraph descriptor flags are not complete. Ensure markDescriptorModules() runs after the last graph mutation " +
    "before computePluginContentFromDslSpec."
  }
  val descriptorBackedModules = HashSet<ContentModuleName>()
  pluginGraph.query {
    contentModules { module ->
      if (module.hasDescriptor) {
        descriptorBackedModules.add(module.contentName())
      }
    }
  }
  val missingDescriptorModules = declaredContentModuleNames
    .asSequence()
    .filterNot { it.isSlashNotation() }
    .filterNot { it in descriptorBackedModules }
    .toList()
  require(missingDescriptorModules.isEmpty()) {
    "PluginGraph is missing descriptor flags for declared DSL test plugin modules: " +
    missingDescriptorModules.joinToString { it.value } +
    ". Ensure markDescriptorModules() runs before computePluginContentFromDslSpec."
  }
  val additionalBundledContentModules = collectPluginContentModules(pluginGraph, testPluginSpec.additionalBundledPluginTargetNames)
  val resolvableModuleNames = HashSet(resolvableModules)
  resolvableModuleNames.addAll(additionalBundledContentModules)
  val bundledPluginNames = resolutionContext.resolveBundledPlugins(productName)
  val allowedMissingPluginIds = testPluginSpec.allowedMissingPluginIds.mapTo(HashSet()) { it.value }
  fun pluginIdValues(ids: List<PluginId>): Set<String> {
    if (ids.isEmpty()) return emptySet()
    return ids.mapTo(LinkedHashSet()) { it.value }
  }
  val explicitAllowedMissingPluginIdsByModule: HashMap<ContentModuleName, Set<String>> = testPluginSpec.spec.additionalModules
    .asSequence()
    .filter { it.allowedMissingPluginIds.isNotEmpty() }
    .associateTo(HashMap()) { it.name to pluginIdValues(it.allowedMissingPluginIds) }
  val explicitAllowedMissingPluginIdsByName: HashMap<String, Set<String>> = testPluginSpec.spec.additionalModules
    .asSequence()
    .filter { it.allowedMissingPluginIds.isNotEmpty() }
    .associateTo(HashMap()) { it.name.value to pluginIdValues(it.allowedMissingPluginIds) }
  fun isBundledPluginContent(moduleName: ContentModuleName): Boolean {
    return pluginGraph.query {
      val moduleNode = contentModule(moduleName) ?: return@query false
      val productNode = product(productName) ?: return@query false
      var bundled = false
      moduleNode.owningPlugins { plugin ->
        if (containsEdge(EDGE_BUNDLES, productNode.id, plugin.id)) {
          bundled = true
        }
      }
      bundled
    }
  }

  fun findOwningPlugins(moduleName: ContentModuleName): Set<OwningPlugin> {
    return resolutionContext.resolveOwningPlugins(moduleName)
  }

  fun isPluginModule(moduleName: ContentModuleName): Boolean {
    return pluginGraph.query { nodeId(moduleName.value, NODE_PLUGIN) >= 0 }
  }

  fun hasAnyContentSource(moduleName: ContentModuleName): Boolean {
    return pluginGraph.query {
      val module = contentModule(moduleName) ?: return@query false
      hasContentSource(module.id)
    }
  }

  suspend fun collectDescriptorModuleDeps(moduleName: ContentModuleName): List<ContentModuleName> {
    if (!moduleName.isTestDescriptor()) return emptyList()
    val descriptor = descriptorCache?.getOrAnalyze(moduleName.value) ?: return emptyList()
    return descriptor.existingModuleDependencies.map(::ContentModuleName)
  }

  // Recursively collect JPS module dependencies (production + test runtime + PROVIDED)
  // and auto-add ONLY UNRESOLVABLE ones with module descriptors.
  // Uses BFS to find all transitive dependencies.
  val autoAddedModules = mutableListOf<ContentModuleInfo>()
  val processedModules = HashSet(declaredContentModuleNames)
  val parentByModule = HashMap<ContentModuleName, ContentModuleName?>()
  val rootDeclaredModuleByModule = HashMap<ContentModuleName, ContentModuleName>()
  val allowedMissingPluginIdsByModule = HashMap<ContentModuleName, Set<String>>()
  val queue = ArrayDeque<ContentModuleName>()
  fun resolveTargetName(moduleName: ContentModuleName): String? {
    if (moduleName.isSlashNotation()) return null
    return if (moduleName.isTestDescriptor()) moduleName.baseModuleName().value else moduleName.value
  }

  data class TargetDependencyInfo(
    val moduleName: ContentModuleName,
    val scope: TargetDependencyScope?,
  )

  fun collectTargetDependencies(moduleName: ContentModuleName): List<TargetDependencyInfo> {
    val targetName = resolveTargetName(moduleName) ?: return emptyList()
    return pluginGraph.query {
      val targetNode = target(targetName) ?: return@query emptyList()
      val result = ArrayList<TargetDependencyInfo>()
      targetNode.dependsOn { dep ->
        val depName = ContentModuleName(name(dep.targetId))
        result.add(TargetDependencyInfo(depName, dep.scope()))
      }
      result
    }
  }

  // Start with declared modules
  for (module in allModules) {
    queue.add(module.name)
    parentByModule.put(module.name, null)
    rootDeclaredModuleByModule.put(module.name, module.name)
    allowedMissingPluginIdsByModule.put(module.name, pluginIdValues(module.allowedMissingPluginIds))
  }

  debug("dslTestDeps") {
    "dslTestPlugin=${testPluginSpec.pluginId.value} declared=${declaredContentModuleNames.size} " +
    "resolvable=${resolvableModuleNames.size} additionalBundled=${additionalBundledContentModules.size}"
  }

  while (queue.isNotEmpty()) {
    val moduleName = queue.removeFirst()
    val rootModule = rootDeclaredModuleByModule.get(moduleName) ?: moduleName
    val moduleSuppressedModules = suppressionConfig.getSuppressedModules(moduleName)
    val rootSuppressedModules = if (rootModule == moduleName) {
      emptySet()
    }
    else {
      suppressionConfig.getSuppressedModules(rootModule)
    }
    val suppressedModules = if (rootSuppressedModules.isEmpty()) {
      moduleSuppressedModules
    }
    else {
      moduleSuppressedModules + rootSuppressedModules
    }
    val isStrictModule = moduleName in strictModules
    // Test descriptor modules can declare additional deps in their own XML.
    val descriptorDeps = collectDescriptorModuleDeps(moduleName)

    fun processDependency(depName: ContentModuleName, scopeName: String?) {
      if (depName in suppressedModules) {
        val suppressionSource = if (depName in rootSuppressedModules) rootModule else moduleName
        suppressionUsageSink?.add(SuppressionUsage(suppressionSource, depName.value, SuppressionType.MODULE_DEP))
        if (!isStrictModule && hasAnyContentSource(depName)) {
          debug("dslTestDeps") { "skip suppressed dep=$depName from=$moduleName (has content source)" }
          return
        }
        debug("dslTestDeps") { "process suppressed dep=$depName from=$moduleName (required or orphan)" }
      }

      if (!processedModules.add(depName)) {
        return
      }

      parentByModule.putIfAbsent(depName, moduleName)
      rootDeclaredModuleByModule.putIfAbsent(depName, rootDeclaredModuleByModule.get(moduleName) ?: moduleName)
      allowedMissingPluginIdsByModule.putIfAbsent(depName, allowedMissingPluginIdsByModule.get(moduleName) ?: emptySet())

      if (depName in TEST_PLUGIN_AUTO_ADD_EXCLUDED_MODULES) {
        debug("dslTestDeps") { "skip injected dep=$depName from=$moduleName" }
        return
      }

      // Already resolvable via module sets or plugin content - skip auto-add and don't traverse
      if (depName in resolvableModuleNames) {
        debug("dslTestDeps") { "skip resolvable dep=$depName from=$moduleName" }
        return
      }

      if (isBundledPluginContent(depName)) {
        debug("dslTestDeps") { "skip bundled production plugin content dep=$depName from=$moduleName" }
        return
      }

      val depTargetName = resolveTargetName(depName)
      if (depTargetName == null || pluginGraph.query { target(depTargetName) == null }) {
        return
      }

      if (isPluginModule(depName)) {
        debug("dslTestDeps") { "skip plugin dep=$depName from=$moduleName" }
        return
      }

      val isLibraryModule = depName.value.startsWith(LIB_MODULE_PREFIX)
      // Skip content modules that belong to plugins; error if the owning plugin isn't resolvable.
      val owningPlugins = if (isLibraryModule) emptySet() else findOwningPlugins(depName)
      // Ignore test-plugin owners: DSL test plugins must be self-contained and cannot rely on other test plugins,
      // so their content modules should be treated as not plugin-owned for auto-add.
      val owningProdPlugins = owningPlugins.filterNot { it.isTest }
      if (owningPlugins.isNotEmpty() && owningProdPlugins.isEmpty()) {
        debug("dslTestDeps") {
          "ignore test-plugin owners dep=$depName owners=${owningPlugins.joinToString { it.pluginId.value }}"
        }
      }
      if (owningProdPlugins.isNotEmpty()) {
        val moduleAllowedMissingPluginIds = allowedMissingPluginIdsByModule.get(moduleName) ?: emptySet()
        val explicitModuleAllowedMissingPluginIds = explicitAllowedMissingPluginIdsByModule.get(moduleName)
                                                ?: explicitAllowedMissingPluginIdsByName.get(moduleName.value)
                                                ?: emptySet()
        val effectiveModuleAllowedMissingPluginIds = if (moduleAllowedMissingPluginIds.isEmpty()) {
          explicitModuleAllowedMissingPluginIds
        }
        else if (explicitModuleAllowedMissingPluginIds.isEmpty()) {
          moduleAllowedMissingPluginIds
        }
        else {
          LinkedHashSet<String>(moduleAllowedMissingPluginIds.size + explicitModuleAllowedMissingPluginIds.size).apply {
            addAll(moduleAllowedMissingPluginIds)
            addAll(explicitModuleAllowedMissingPluginIds)
          }
        }
        val effectiveAllowedMissingPluginIds = if (effectiveModuleAllowedMissingPluginIds.isEmpty()) {
          allowedMissingPluginIds
        }
        else {
          LinkedHashSet<String>(allowedMissingPluginIds.size + effectiveModuleAllowedMissingPluginIds.size).apply {
            addAll(allowedMissingPluginIds)
            addAll(effectiveModuleAllowedMissingPluginIds)
          }
        }
        validateDslTestPluginOwnedDependency(
          depName = depName,
          moduleName = moduleName,
          scopeName = scopeName,
          isDeclaredInSpec = moduleName in declaredContentModuleNames,
          declaredRootModule = rootModule,
          testPluginSpec = testPluginSpec,
          productName = productName,
          bundledPluginNames = bundledPluginNames,
          allowedMissingPluginIds = effectiveAllowedMissingPluginIds,
          owningProdPlugins = owningProdPlugins,
          updateSuppressions = updateSuppressions,
          suppressionUsageSink = suppressionUsageSink,
          errorSink = errorSink,
        )
        return
      }

      // Descriptor discovery rules:
      // - The graph is the source of truth for descriptor presence, even if content sources are incomplete.
      // - Model building pre-marks all JPS targets with descriptors so auto-add decisions can rely on the graph.
      // - This does NOT mutate the graph here; the module is registered later when addPluginWithContent runs.
      val hasDescriptorInGraph = pluginGraph.query {
        val moduleNode = contentModule(depName) ?: return@query false
        moduleNode.hasDescriptor
      }
      if (!hasDescriptorInGraph) {
        debug("dslTestDeps") { "skip no descriptor dep=$depName from=$moduleName" }
        return
      }
      autoAddedModules.add(ContentModuleInfo(name = depName, loadingMode = autoAddedModulesLoadingMode))
      strictModules.add(depName)
      if (dependencyChainsSink != null) {
        val chain = ArrayList<ContentModuleName>()
        var current: ContentModuleName? = depName
        val seen = HashSet<ContentModuleName>()
        while (current != null && seen.add(current)) {
          chain.add(current)
          current = parentByModule.get(current)
        }
        dependencyChainsSink.put(depName, chain.asReversed())
      }
      debug("dslTestDeps") { "auto-add dep=$depName from=$moduleName" }
      // Continue BFS to also process dependencies of this auto-added module
      queue.add(depName)
    }

    val targetDeps = collectTargetDependencies(moduleName)
    for (dep in targetDeps) {
      processDependency(dep.moduleName, dep.scope?.name)
    }

    for (depName in descriptorDeps) {
      processDependency(depName, null)
    }
  }

  // Combine declared modules with auto-added ones
  val contentModules = allModules.map { ContentModuleInfo(name = it.name, loadingMode = it.loading) } + autoAddedModules

  return PluginContentInfo(
    pluginXmlPath = projectRoot.resolve(testPluginSpec.pluginXmlPath),
    pluginXmlContent = "",  // Will be generated by generateTestPluginXml
    pluginId = testPluginSpec.pluginId,
    contentModules = contentModules,
    moduleDependencies = emptySet(),
    source = PluginSource.DSL_TEST,  // DSL-defined test plugin
  )
}
