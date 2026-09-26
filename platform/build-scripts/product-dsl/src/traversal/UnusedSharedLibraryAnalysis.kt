// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.productLayout.traversal

import com.intellij.platform.pluginGraph.ContentModuleNode
import com.intellij.platform.pluginGraph.GraphScope
import com.intellij.platform.pluginGraph.PluginGraph
import com.intellij.platform.pluginGraph.PluginNode
import com.intellij.platform.pluginGraph.ProductNode
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue
import kotlinx.serialization.Serializable
import org.jetbrains.intellij.build.productLayout.LIB_MODULE_PREFIX

@Serializable
internal data class UnusedSharedLibraryModulesResult(
  @JvmField val violations: List<UnusedSharedLibraryModuleViolation>,
  @JvmField val overShipped: List<OverShippedSharedLibraryModule>,
  @JvmField val summary: UnusedSharedLibraryModulesSummary,
)

@Serializable
internal data class UnusedSharedLibraryModulesSummary(
  @JvmField val totalViolations: Int,
  @JvmField val totalOverShipped: Int,
  @JvmField val affectedModuleSets: List<String>,
)

/**
 * A library module declared as ordinary (non-embedded) module-set content that nothing declares a dependency on.
 */
@Serializable
internal data class UnusedSharedLibraryModuleViolation(
  @JvmField val module: String,
  @JvmField val declaringModuleSets: List<String>,
  @JvmField val availableProducts: List<String>,
)

/**
 * A library module that has consumers, but is shipped in products where none of those consumers is available.
 *
 * Diagnostic only: shipping a shared library more widely than its consumers is a layout smell, not a build
 * error, so this is reported through the analysis JSON rather than failing validation.
 */
@Serializable
internal data class OverShippedSharedLibraryModule(
  @JvmField val module: String,
  @JvmField val declaringModuleSets: List<String>,
  @JvmField val productsWithoutConsumer: List<String>,
  @JvmField val moduleConsumers: List<String>,
  @JvmField val pluginConsumers: List<String>,
)

/**
 * Companion of [analyzeUnusedEmbeddedLibraryModules] for the other loading mode.
 *
 * Where that analysis asks "does this library deserve the core classloader?", this one asks
 * "does this library deserve to be in the product at all?".
 */
internal fun analyzeUnusedSharedLibraryModules(graph: PluginGraph): UnusedSharedLibraryModulesResult {
  return graph.query {
    val candidates = collectSharedLibraryCandidates()
    val moduleConsumers = HashMap<Int, MutableSet<Int>>()
    val pluginConsumers = HashMap<Int, MutableSet<Int>>()
    collectConsumers(candidates.keys, moduleConsumers, pluginConsumers)

    val violations = ArrayList<UnusedSharedLibraryModuleViolation>()
    val overShipped = ArrayList<OverShippedSharedLibraryModule>()
    for ((moduleId, moduleSets) in candidates) {
      val module = ContentModuleNode(moduleId)
      val consumerModules = moduleConsumers.get(moduleId).orEmpty()
      val consumerPlugins = pluginConsumers.get(moduleId).orEmpty()
      val declaringModuleSets = moduleSets.sorted()
      if (consumerModules.isEmpty() && consumerPlugins.isEmpty()) {
        violations.add(UnusedSharedLibraryModuleViolation(
          module = module.contentName().value,
          declaringModuleSets = declaringModuleSets,
          availableProducts = collectProductsProviding(module),
        ))
        continue
      }

      val productsWithoutConsumer = collectProductsWithoutConsumer(module, consumerModules, consumerPlugins)
      if (productsWithoutConsumer.isNotEmpty()) {
        overShipped.add(OverShippedSharedLibraryModule(
          module = module.contentName().value,
          declaringModuleSets = declaringModuleSets,
          productsWithoutConsumer = productsWithoutConsumer,
          moduleConsumers = consumerModules.map { ContentModuleNode(it).contentName().value }.sorted(),
          pluginConsumers = consumerPlugins.map { PluginNode(it).name().value }.sorted(),
        ))
      }
    }

    violations.sortBy { it.module }
    overShipped.sortBy { it.module }
    UnusedSharedLibraryModulesResult(
      violations = violations,
      overShipped = overShipped,
      summary = UnusedSharedLibraryModulesSummary(
        totalViolations = violations.size,
        totalOverShipped = overShipped.size,
        affectedModuleSets = violations.flatMapTo(LinkedHashSet()) { it.declaringModuleSets }.sorted(),
      ),
    )
  }
}

private fun GraphScope.collectSharedLibraryCandidates(): Map<Int, Set<String>> {
  val candidates = LinkedHashMap<Int, MutableSet<String>>()
  moduleSets { moduleSet ->
    moduleSet.containsModule { module, loading ->
      if (loading != ModuleLoadingRuleValue.EMBEDDED && module.contentName().value.startsWith(LIB_MODULE_PREFIX)) {
        candidates.computeIfAbsent(module.id) { LinkedHashSet() }.add(moduleSet.name())
      }
    }
  }
  return candidates
}

private fun GraphScope.collectConsumers(
  candidateIds: Set<Int>,
  moduleConsumers: MutableMap<Int, MutableSet<Int>>,
  pluginConsumers: MutableMap<Int, MutableSet<Int>>,
) {
  contentModules { module ->
    if (module.id in candidateIds && module.contentName().value.startsWith(LIB_MODULE_PREFIX)) {
      // a library wrapper depending on another library wrapper does not justify shipping either of them
      return@contentModules
    }
    module.dependsOn { dependency ->
      if (dependency.id in candidateIds) {
        moduleConsumers.computeIfAbsent(dependency.id) { LinkedHashSet() }.add(module.id)
      }
    }
    module.dependsOnTest { dependency ->
      if (dependency.id in candidateIds) {
        moduleConsumers.computeIfAbsent(dependency.id) { LinkedHashSet() }.add(module.id)
      }
    }
  }
  plugins { plugin ->
    plugin.dependsOnContentModule { dependency ->
      if (dependency.id in candidateIds) {
        pluginConsumers.computeIfAbsent(dependency.id) { LinkedHashSet() }.add(plugin.id)
      }
    }
  }
}

private fun GraphScope.collectProductsProviding(module: ContentModuleNode): List<String> {
  val result = ArrayList<String>()
  products { product ->
    if (product.containsAvailableContentModule(module)) {
      result.add(product.name())
    }
  }
  return result.sorted()
}

private fun GraphScope.collectProductsWithoutConsumer(
  module: ContentModuleNode,
  consumerModules: Set<Int>,
  consumerPlugins: Set<Int>,
): List<String> {
  val bundlingProductIds = HashSet<Int>()
  for (pluginId in consumerPlugins) {
    PluginNode(pluginId).bundledByProducts { product -> bundlingProductIds.add(product.id) }
  }

  val result = ArrayList<String>()
  products { product ->
    if (!product.containsAvailableContentModule(module)) {
      return@products
    }
    if (product.id in bundlingProductIds || hasAvailableConsumer(product, consumerModules)) {
      return@products
    }
    result.add(product.name())
  }
  return result.sorted()
}

private fun GraphScope.hasAvailableConsumer(product: ProductNode, consumerModules: Set<Int>): Boolean {
  return consumerModules.any { product.containsAvailableContentModule(ContentModuleNode(it)) }
}
