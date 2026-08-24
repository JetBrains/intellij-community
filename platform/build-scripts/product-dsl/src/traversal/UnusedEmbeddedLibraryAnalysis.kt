// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.productLayout.traversal

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.ContentModuleNode
import com.intellij.platform.pluginGraph.ContentSourceKind
import com.intellij.platform.pluginGraph.GraphScope
import com.intellij.platform.pluginGraph.ModuleSetNode
import com.intellij.platform.pluginGraph.PluginGraph
import com.intellij.platform.pluginGraph.ProductNode
import com.intellij.platform.pluginGraph.TargetDependencyScope
import com.intellij.platform.pluginGraph.TargetNode
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue
import kotlinx.serialization.Serializable
import org.jetbrains.intellij.build.productLayout.LIB_MODULE_PREFIX
import org.jetbrains.intellij.build.productLayout.ProductModulesContentSpec

@Serializable
internal data class UnusedEmbeddedLibraryModulesResult(
  @JvmField val violations: List<UnusedEmbeddedLibraryModuleViolation>,
  @JvmField val summary: UnusedEmbeddedLibraryModulesSummary,
)

@Serializable
internal data class UnusedEmbeddedLibraryModulesSummary(
  @JvmField val totalViolations: Int,
  @JvmField val affectedModuleSets: List<String>,
)

@Serializable
internal data class UnusedEmbeddedLibraryModuleViolation(
  @JvmField val module: String,
  @JvmField val declaringModuleSets: List<String>,
  @JvmField val availableProducts: List<String>,
  @JvmField val platformConsumers: List<String>,
  @JvmField val productionPluginConsumers: List<String>,
  @JvmField val testPluginConsumers: List<String>,
)

/**
 * Finds library content modules which are embedded in module sets but are not reachable from
 * embedded, non-library product content through an all-embedded production dependency path.
 */
internal fun analyzeUnusedEmbeddedLibraryModules(
  graph: PluginGraph,
  productSpecsByName: Map<String, ProductModulesContentSpec> = emptyMap(),
): UnusedEmbeddedLibraryModulesResult {
  return graph.query {
    val candidates = collectEmbeddedLibraryCandidates()
    val liveCandidates = collectLiveEmbeddedLibraries(candidates.keys, productSpecsByName)
    val productionPluginConsumers = collectPluginConsumers(candidates.keys, includeTestPlugins = false)
    val testPluginConsumers = collectPluginConsumers(candidates.keys, includeTestPlugins = true)
    val platformConsumers = collectPlatformConsumers(candidates.keys)

    val violations = candidates.entries.asSequence()
      .filter { it.key !in liveCandidates }
      .map { (moduleId, moduleSets) ->
        val module = ContentModuleNode(moduleId)
        UnusedEmbeddedLibraryModuleViolation(
          module = module.contentName().value,
          declaringModuleSets = moduleSets.sorted(),
          availableProducts = collectAvailableProducts(module),
          platformConsumers = platformConsumers.get(moduleId).orEmpty().sorted(),
          productionPluginConsumers = productionPluginConsumers.get(moduleId).orEmpty().sorted(),
          testPluginConsumers = testPluginConsumers.get(moduleId).orEmpty().sorted(),
        )
      }
      .sortedBy { it.module }
      .toList()

    UnusedEmbeddedLibraryModulesResult(
      violations = violations,
      summary = UnusedEmbeddedLibraryModulesSummary(
        totalViolations = violations.size,
        affectedModuleSets = violations.flatMapTo(LinkedHashSet()) { it.declaringModuleSets }.sorted(),
      ),
    )
  }
}

/**
 * Libraries which belong in the core classloader no matter what the content graph says.
 *
 * The only accepted criterion: the library's classes are resolved from classloaders the layout cannot enumerate -
 * generated proxies defined in a plugin classloader, or a factory/SPI lookup by class name from arbitrary code.
 * An explicit `<dependencies><module .../></dependencies>` edge cannot express "every plugin", so such a library has
 * to be embedded even though no embedded platform content depends on it.
 *
 * A library that is merely convenient to have everywhere does not belong here - demote it and add the edges.
 */
private val CORE_CLASSLOADER_ONLY_LIBRARIES: Set<String> = setOf(
  // `AdvancedEnhancer.getDefaultClassLoader()` defines each generated DOM proxy in the `PluginClassLoader` of one of
  // the proxied interfaces, so `net.sf.cglib.proxy.Factory` must be resolvable from any plugin classloader.
  "intellij.libraries.cglib",
)

private fun GraphScope.collectEmbeddedLibraryCandidates(): Map<Int, Set<String>> {
  val candidates = LinkedHashMap<Int, MutableSet<String>>()
  moduleSets { moduleSet ->
    moduleSet.containsModule { module, loading ->
      if (loading == ModuleLoadingRuleValue.EMBEDDED) {
        val name = module.contentName().value
        if (name.startsWith(LIB_MODULE_PREFIX) && name !in CORE_CLASSLOADER_ONLY_LIBRARIES) {
          candidates.computeIfAbsent(module.id) { LinkedHashSet() }.add(moduleSet.name())
        }
      }
    }
  }
  return candidates
}

private fun GraphScope.collectLiveEmbeddedLibraries(
  candidateIds: Set<Int>,
  productSpecsByName: Map<String, ProductModulesContentSpec>,
): Set<Int> {
  val result = HashSet<Int>()
  products { product ->
    collectLibrariesUsedByEmbeddedProductTargets(product, candidateIds, result)

    val visited = HashSet<Int>()
    val queue = ArrayDeque<ContentModuleNode>()
    collectProductContentModules(product).asSequence()
      .filterNot { it.contentName().value.startsWith(LIB_MODULE_PREFIX) }
      .filter { isEmbeddedInProductContent(it, product) }
      .forEach { root ->
        if (visited.add(root.id)) {
          queue.add(root)
        }
      }

    while (queue.isNotEmpty()) {
      queue.removeFirst().dependsOn { dependency ->
        if (!isEmbeddedInProductContent(dependency, product)) {
          return@dependsOn
        }
        if (dependency.id in candidateIds) {
          result.add(dependency.id)
        }
        if (visited.add(dependency.id)) {
          queue.add(dependency)
        }
      }
    }

    val spec = productSpecsByName.get(product.name()) ?: return@products
    val implicitAnalysis = analyzeImplicitEmbeddedContentModules(product, spec)
    for ((dependencyName, origins) in implicitAnalysis.reachedModules) {
      val dependency = contentModule(dependencyName) ?: continue
      if (dependency.id !in candidateIds || !isEmbeddedInProductContent(dependency, product)) continue
      if (origins.any { originName ->
          !originName.value.startsWith(LIB_MODULE_PREFIX) &&
          contentModule(originName)?.let { isEmbeddedInProductContent(it, product) } == true
        }) {
        result.add(dependency.id)
      }
    }
  }
  return result
}

private fun GraphScope.collectLibrariesUsedByEmbeddedProductTargets(
  product: ProductNode,
  candidateIds: Set<Int>,
  result: MutableSet<Int>,
) {
  val visited = HashSet<Int>()
  val queue = ArrayDeque<TargetNode>()
  collectProductContentModules(product).asSequence()
    .filterNot { it.contentName().value.startsWith(LIB_MODULE_PREFIX) }
    .filter { isEmbeddedInProductContent(it, product) }
    .forEach { module ->
      module.backedBy { target ->
        if (visited.add(target.id)) {
          queue.add(target)
        }
      }
    }

  while (queue.isNotEmpty()) {
    queue.removeFirst().dependsOn { dependency ->
      val scope = dependency.scope()
      if (scope == TargetDependencyScope.TEST || scope == TargetDependencyScope.PROVIDED) {
        return@dependsOn
      }

      val dependencyModule = contentModule(ContentModuleName(name(dependency.targetId)))
      if (dependencyModule != null) {
        if (dependencyModule.id in candidateIds && isEmbeddedInProductContent(dependencyModule, product)) {
          result.add(dependencyModule.id)
        }
        if (dependencyModule.hasDescriptor && !isEmbeddedInProductContent(dependencyModule, product)) {
          return@dependsOn
        }
      }

      if (visited.add(dependency.targetId)) {
        queue.add(TargetNode(dependency.targetId))
      }
    }
  }
}

private fun GraphScope.collectPluginConsumers(candidateIds: Set<Int>, includeTestPlugins: Boolean): Map<Int, Set<String>> {
  val result = HashMap<Int, MutableSet<String>>()
  plugins { plugin ->
    if (plugin.isTest != includeTestPlugins) {
      return@plugins
    }
    val roots = ArrayList<ContentModuleNode>()
    if (includeTestPlugins) {
      plugin.containsContentTest { module, _ -> roots.add(module) }
    }
    else {
      plugin.containsContent { module, _ -> roots.add(module) }
    }
    plugin.dependsOnContentModule { module -> roots.add(module) }
    collectReachableCandidates(roots, candidateIds, includeTestDependencies = includeTestPlugins) { candidateId ->
      result.computeIfAbsent(candidateId) { LinkedHashSet() }.add(plugin.name().value)
    }
  }
  return result
}

private fun GraphScope.collectReachableCandidates(
  roots: List<ContentModuleNode>,
  candidateIds: Set<Int>,
  includeTestDependencies: Boolean,
  consumer: (Int) -> Unit,
) {
  val visited = HashSet<Int>()
  val queue = ArrayDeque<ContentModuleNode>()
  for (root in roots) {
    if (root.id in candidateIds) {
      consumer(root.id)
    }
    if (visited.add(root.id)) {
      queue.add(root)
    }
  }
  while (queue.isNotEmpty()) {
    val module = queue.removeFirst()
    fun visit(dependency: ContentModuleNode) {
      if (dependency.id in candidateIds) {
        consumer(dependency.id)
      }
      if (visited.add(dependency.id)) {
        queue.add(dependency)
      }
    }
    module.dependsOn { visit(it) }
    if (includeTestDependencies) {
      module.dependsOnTest { visit(it) }
    }
  }
}

private fun GraphScope.collectPlatformConsumers(candidateIds: Set<Int>): Map<Int, Set<String>> {
  val result = HashMap<Int, MutableSet<String>>()
  contentModules { module ->
    if (!hasNonPluginProductionSource(module)) {
      return@contentModules
    }
    module.dependsOn { dependency ->
      if (dependency.id in candidateIds) {
        result.computeIfAbsent(dependency.id) { LinkedHashSet() }.add(module.contentName().value)
      }
    }
  }
  return result
}

private fun GraphScope.hasNonPluginProductionSource(module: ContentModuleNode): Boolean {
  var result = false
  module.contentProductionSources { source ->
    if (source.kind != ContentSourceKind.PLUGIN) {
      result = true
    }
  }
  return result
}

private fun GraphScope.collectAvailableProducts(module: ContentModuleNode): List<String> {
  val result = ArrayList<String>()
  products { product ->
    if (hasNonPluginSourceInProduct(module, product)) {
      result.add(product.name())
    }
  }
  return result.sorted()
}

private fun GraphScope.collectProductContentModules(product: ProductNode): List<ContentModuleNode> {
  val result = LinkedHashMap<Int, ContentModuleNode>()
  product.includesModuleSet { moduleSet ->
    moduleSet.modulesRecursive { module -> result.put(module.id, module) }
  }
  product.containsContent { module, _ -> result.put(module.id, module) }
  return result.values.toList()
}

private fun GraphScope.isEmbeddedInProductContent(module: ContentModuleNode, product: ProductNode): Boolean {
  var hasSource = false
  var embedded = true
  module.contentProductionSources { source ->
    val loading = when (source.kind) {
      ContentSourceKind.PRODUCT -> {
        val sourceProduct = source.product()
        if (sourceProduct.id == product.id) loadingFromProduct(sourceProduct, module) else null
      }
      ContentSourceKind.MODULE_SET -> {
        val moduleSet = source.moduleSet()
        if (product.includesModuleSetRecursive(moduleSet)) loadingFromModuleSet(moduleSet, module) else null
      }
      ContentSourceKind.PLUGIN -> null
    }
    if (loading != null) {
      hasSource = true
      embedded = embedded && loading == ModuleLoadingRuleValue.EMBEDDED
    }
  }
  return hasSource && embedded
}

private fun GraphScope.hasNonPluginSourceInProduct(module: ContentModuleNode, product: ProductNode): Boolean {
  var result = false
  module.contentProductionSources { source ->
    result = result || when (source.kind) {
      ContentSourceKind.PRODUCT -> source.product().id == product.id
      ContentSourceKind.MODULE_SET -> product.includesModuleSetRecursive(source.moduleSet())
      ContentSourceKind.PLUGIN -> false
    }
  }
  return result
}

private fun GraphScope.loadingFromProduct(product: ProductNode, module: ContentModuleNode): ModuleLoadingRuleValue? {
  var result: ModuleLoadingRuleValue? = null
  product.containsContent { candidate, loading ->
    if (candidate.id == module.id) {
      result = loading
    }
  }
  return result
}

private fun GraphScope.loadingFromModuleSet(moduleSet: ModuleSetNode, module: ContentModuleNode): ModuleLoadingRuleValue? {
  var result: ModuleLoadingRuleValue? = null
  moduleSet.containsModule { candidate, loading ->
    if (candidate.id == module.id) {
      result = loading
    }
  }
  return result
}
