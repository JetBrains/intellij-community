// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.productLayout.traversal

import com.intellij.platform.pluginGraph.ContentModuleNode
import com.intellij.platform.pluginGraph.ContentSourceKind
import com.intellij.platform.pluginGraph.GraphScope
import com.intellij.platform.pluginGraph.ModuleSetNode
import com.intellij.platform.pluginGraph.PluginGraph
import com.intellij.platform.pluginGraph.PluginNode
import com.intellij.platform.pluginGraph.ProductNode
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue
import kotlinx.serialization.Serializable

@Serializable
internal data class EmbeddedDependencyClosureResult(
  @JvmField val violations: List<EmbeddedDependencyClosureViolation>,
  @JvmField val summary: EmbeddedDependencyClosureSummary,
  @JvmField val productFilter: String? = null,
  @JvmField val moduleSetFilter: String? = null,
  @JvmField val moduleFilter: String? = null,
  @JvmField val pluginSourceOnly: Boolean = false,
  @JvmField val error: String? = null,
)

@Serializable
internal data class EmbeddedDependencyClosureSummary(
  @JvmField val totalViolations: Int,
  @JvmField val affectedProducts: List<String>,
  @JvmField val affectedModules: List<String>,
)

@Serializable
internal data class EmbeddedDependencyClosureViolation(
  @JvmField val product: String,
  @JvmField val sourceModule: String,
  @JvmField val dependency: String,
  @JvmField val dependencyPath: List<String>,
  @JvmField val dependencySources: List<EmbeddedDependencySourceInfo>,
)

@Serializable
internal data class EmbeddedDependencySourceInfo(
  @JvmField val kind: String,
  @JvmField val name: String,
  @JvmField val loading: String? = null,
)

internal fun analyzeEmbeddedDependencyClosure(
  graph: PluginGraph,
  productName: String? = null,
  moduleSetName: String? = null,
  moduleName: String? = null,
  pluginSourceOnly: Boolean = false,
): EmbeddedDependencyClosureResult {
  return try {
    graph.query {
      val productsToCheck = collectProductsForEmbeddedClosure(productName)
      if (productName != null && productsToCheck.isEmpty()) {
        return@query emptyEmbeddedDependencyClosureResult(
          productName = productName,
          moduleSetName = moduleSetName,
          moduleName = moduleName,
          pluginSourceOnly = pluginSourceOnly,
          error = "Product '$productName' not found",
        )
      }

      val violations = ArrayList<EmbeddedDependencyClosureViolation>()
      for (product in productsToCheck) {
        violations.addAll(analyzeProductEmbeddedDependencyClosure(product, moduleSetName, moduleName, pluginSourceOnly))
      }
      violations.sortWith(compareBy({ it.product }, { it.sourceModule }, { it.dependency }))

      EmbeddedDependencyClosureResult(
        violations = violations,
        summary = EmbeddedDependencyClosureSummary(
          totalViolations = violations.size,
          affectedProducts = violations.mapTo(LinkedHashSet()) { it.product }.toList(),
          affectedModules = violations.mapTo(LinkedHashSet()) { it.sourceModule }.sorted(),
        ),
        productFilter = productName,
        moduleSetFilter = moduleSetName,
        moduleFilter = moduleName,
        pluginSourceOnly = pluginSourceOnly,
      )
    }
  }
  catch (e: Exception) {
    emptyEmbeddedDependencyClosureResult(
      productName = productName,
      moduleSetName = moduleSetName,
      moduleName = moduleName,
      pluginSourceOnly = pluginSourceOnly,
      error = "Failed to analyze embedded dependency closure: ${e.message}",
    )
  }
}

private fun emptyEmbeddedDependencyClosureResult(
  productName: String?,
  moduleSetName: String?,
  moduleName: String?,
  pluginSourceOnly: Boolean,
  error: String?,
): EmbeddedDependencyClosureResult {
  return EmbeddedDependencyClosureResult(
    violations = emptyList(),
    summary = EmbeddedDependencyClosureSummary(
      totalViolations = 0,
      affectedProducts = emptyList(),
      affectedModules = emptyList(),
    ),
    productFilter = productName,
    moduleSetFilter = moduleSetName,
    moduleFilter = moduleName,
    pluginSourceOnly = pluginSourceOnly,
    error = error,
  )
}

private fun GraphScope.collectProductsForEmbeddedClosure(productName: String?): List<ProductNode> {
  if (productName != null) {
    return listOfNotNull(product(productName))
  }
  val result = ArrayList<ProductNode>()
  products { result.add(it) }
  return result
}

private fun GraphScope.analyzeProductEmbeddedDependencyClosure(
  product: ProductNode,
  moduleSetName: String?,
  moduleName: String?,
  pluginSourceOnly: Boolean,
): List<EmbeddedDependencyClosureViolation> {
  val productName = product.name()
  val violations = ArrayList<EmbeddedDependencyClosureViolation>()
  val roots = collectProductContentModules(product)
    .asSequence()
    .filter { moduleName == null || it.contentName().value == moduleName }
    .filter { isEmbeddedInProductContent(it, product) }
    .filter { moduleSetName == null || hasDirectModuleSetSource(it, product, moduleSetName) }
    .sortedBy { it.contentName().value }
    .toList()

  for (root in roots) {
    collectEmbeddedClosureViolations(product, productName, root, pluginSourceOnly, violations)
  }
  return violations
}

private fun GraphScope.collectProductContentModules(product: ProductNode): List<ContentModuleNode> {
  val result = LinkedHashMap<Int, ContentModuleNode>()
  product.includesModuleSet { moduleSet ->
    moduleSet.modulesRecursive { module -> result.put(module.id, module) }
  }
  product.containsContent { module, _ -> result.put(module.id, module) }
  return result.values.toList()
}

private data class EmbeddedQueueEntry(
  val module: ContentModuleNode,
  val path: List<String>,
)

private fun GraphScope.collectEmbeddedClosureViolations(
  product: ProductNode,
  productName: String,
  root: ContentModuleNode,
  pluginSourceOnly: Boolean,
  output: MutableList<EmbeddedDependencyClosureViolation>,
) {
  val visited = HashSet<Int>()
  val reported = HashSet<Int>()
  val queue = ArrayDeque<EmbeddedQueueEntry>()
  visited.add(root.id)
  queue.add(EmbeddedQueueEntry(root, listOf(root.contentName().value)))

  while (queue.isNotEmpty()) {
    val entry = queue.removeFirst()
    entry.module.dependsOn { dep ->
      if (!product.containsAvailableContentModule(dep)) {
        return@dependsOn
      }

      val depName = dep.contentName().value
      val path = entry.path + depName
      if (!isEmbeddedInProductContent(dep, product)) {
        val dependencySources = collectRelevantSourceInfos(dep, product)
        if ((!pluginSourceOnly || dependencySources.any { it.kind == "plugin" }) && reported.add(dep.id)) {
          output.add(EmbeddedDependencyClosureViolation(
            product = productName,
            sourceModule = root.contentName().value,
            dependency = depName,
            dependencyPath = path,
            dependencySources = dependencySources,
          ))
        }
        return@dependsOn
      }

      if (visited.add(dep.id)) {
        queue.add(EmbeddedQueueEntry(dep, path))
      }
    }
  }
}

private fun GraphScope.isEmbeddedInProductContent(module: ContentModuleNode, product: ProductNode): Boolean {
  val sources = collectRelevantNonPluginSourceInfos(module, product)
  return sources.isNotEmpty() && sources.all { it.loading == ModuleLoadingRuleValue.EMBEDDED.name }
}

private fun GraphScope.hasDirectModuleSetSource(module: ContentModuleNode, product: ProductNode, moduleSetName: String): Boolean {
  return collectRelevantNonPluginSourceInfos(module, product).any { it.kind == "moduleSet" && it.name == moduleSetName }
}

private fun GraphScope.collectRelevantNonPluginSourceInfos(
  module: ContentModuleNode,
  product: ProductNode,
): List<EmbeddedDependencySourceInfo> {
  val result = ArrayList<EmbeddedDependencySourceInfo>()
  module.contentProductionSources { source ->
    when (source.kind) {
      ContentSourceKind.PRODUCT -> {
        val sourceProduct = source.product()
        if (sourceProduct.id == product.id) {
          result.add(EmbeddedDependencySourceInfo(
            kind = "product",
            name = sourceProduct.name(),
            loading = loadingFromProduct(sourceProduct, module)?.name,
          ))
        }
      }
      ContentSourceKind.MODULE_SET -> {
        val sourceModuleSet = source.moduleSet()
        if (product.includesModuleSetRecursive(sourceModuleSet)) {
          result.add(EmbeddedDependencySourceInfo(
            kind = "moduleSet",
            name = sourceModuleSet.name(),
            loading = loadingFromModuleSet(sourceModuleSet, module)?.name,
          ))
        }
      }
      ContentSourceKind.PLUGIN -> {}
    }
  }
  return result.sortedWith(compareBy({ it.kind }, { it.name }))
}

private fun GraphScope.collectRelevantSourceInfos(
  module: ContentModuleNode,
  product: ProductNode,
): List<EmbeddedDependencySourceInfo> {
  val result = ArrayList<EmbeddedDependencySourceInfo>()
  module.contentProductionSources { source ->
    when (source.kind) {
      ContentSourceKind.PRODUCT -> {
        val sourceProduct = source.product()
        if (sourceProduct.id == product.id) {
          result.add(EmbeddedDependencySourceInfo("product", sourceProduct.name(), loadingFromProduct(sourceProduct, module)?.name))
        }
      }
      ContentSourceKind.MODULE_SET -> {
        val sourceModuleSet = source.moduleSet()
        if (product.includesModuleSetRecursive(sourceModuleSet)) {
          result.add(EmbeddedDependencySourceInfo("moduleSet", sourceModuleSet.name(), loadingFromModuleSet(sourceModuleSet, module)?.name))
        }
      }
      ContentSourceKind.PLUGIN -> {
        val sourcePlugin = source.plugin()
        if (productBundlesPlugin(product, sourcePlugin)) {
          result.add(EmbeddedDependencySourceInfo("plugin", sourcePlugin.name().value, loadingFromPlugin(sourcePlugin, module)?.name))
        }
      }
    }
  }
  return result.sortedWith(compareBy({ it.kind }, { it.name }))
}

private fun GraphScope.loadingFromProduct(product: ProductNode, module: ContentModuleNode): ModuleLoadingRuleValue? {
  var loading: ModuleLoadingRuleValue? = null
  product.containsContent { contentModule, mode ->
    if (contentModule.id == module.id) {
      loading = mode
    }
  }
  return loading
}

private fun GraphScope.loadingFromModuleSet(moduleSet: ModuleSetNode, module: ContentModuleNode): ModuleLoadingRuleValue? {
  var loading: ModuleLoadingRuleValue? = null
  moduleSet.containsModule { contentModule, mode ->
    if (contentModule.id == module.id) {
      loading = mode
    }
  }
  return loading
}

private fun GraphScope.loadingFromPlugin(plugin: PluginNode, module: ContentModuleNode): ModuleLoadingRuleValue? {
  var loading: ModuleLoadingRuleValue? = null
  plugin.containsContent { contentModule, mode ->
    if (contentModule.id == module.id) {
      loading = mode
    }
  }
  return loading
}

private fun GraphScope.productBundlesPlugin(product: ProductNode, plugin: PluginNode): Boolean {
  var result = false
  product.bundles { bundledPlugin ->
    if (bundledPlugin.id == plugin.id) {
      result = true
    }
  }
  return result
}
