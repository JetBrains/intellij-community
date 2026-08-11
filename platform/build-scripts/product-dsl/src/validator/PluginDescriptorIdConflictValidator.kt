// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.validator

import com.intellij.platform.pluginGraph.PluginGraph
import com.intellij.platform.pluginGraph.PluginId
import com.intellij.platform.pluginGraph.PluginModuleId
import com.intellij.platform.pluginGraph.PluginNode
import com.intellij.platform.pluginGraph.ProductNode
import com.intellij.platform.pluginGraph.TargetName
import com.intellij.platform.pluginGraph.contentName
import org.jetbrains.intellij.build.productLayout.TestPluginSpec
import org.jetbrains.intellij.build.productLayout.model.error.PluginDescriptorIdConflictError
import org.jetbrains.intellij.build.productLayout.model.error.ValidationError
import org.jetbrains.intellij.build.productLayout.pipeline.ComputeContext
import org.jetbrains.intellij.build.productLayout.pipeline.DataSlot
import org.jetbrains.intellij.build.productLayout.pipeline.NodeIds
import org.jetbrains.intellij.build.productLayout.pipeline.PipelineNode

/**
 * Validates that test plugins do not declare descriptor IDs already provided by production plugins.
 */
internal object PluginDescriptorIdConflictValidator : PipelineNode {
  override val id get() = NodeIds.PLUGIN_DESCRIPTOR_ID_CONFLICT_VALIDATION
  override val requires: Set<DataSlot<*>> get() = emptySet()

  override suspend fun execute(ctx: ComputeContext) {
    val model = ctx.model
    ctx.emitErrorsPerProduct(model.pluginGraph) { product ->
      validateDescriptorIdConflictsForProduct(product, model.pluginGraph, model.dslTestPluginsByProduct)
    }
  }
}

/**
 * A test plugin sees more than the plugins its product bundles: a DSL test plugin may pull extra production plugins into the run via
 * `additionalBundledPluginTargetNames` (mirroring the `-Dadditional.modules=` list of its `intellij.yaml` runner). Those plugins own
 * descriptor IDs at runtime just like bundled ones, so they are checked per test plugin on top of the shared product-bundled owners.
 */
private fun validateDescriptorIdConflictsForProduct(
  productV: ProductNode,
  pluginGraph: PluginGraph,
  dslTestPluginsByProduct: Map<String, List<TestPluginSpec>>,
): List<ValidationError> = pluginGraph.query {
  val productName = productV.name()
  val additionalBundlesByTestPluginId: Map<PluginId, List<TargetName>> = dslTestPluginsByProduct[productName]
    ?.filter { it.additionalBundledPluginTargetNames.isNotEmpty() }
    ?.associate { it.pluginId to it.additionalBundledPluginTargetNames }
    .orEmpty()
  val productionOwners = LinkedHashMap<PluginId, LinkedHashSet<PluginDescriptorIdConflictError.DescriptorOwner>>()

  fun recordPlugin(
    plugin: PluginNode,
    isTest: Boolean,
    target: MutableMap<PluginId, LinkedHashSet<PluginDescriptorIdConflictError.DescriptorOwner>>,
  ) {
    if (plugin.isAlias) {
      return
    }

    val pluginName = plugin.name()
    val pluginIdValue = plugin.pluginIdOrNull ?: return
    target.computeIfAbsent(pluginIdValue) { LinkedHashSet() }
      .add(PluginDescriptorIdConflictError.DescriptorOwner(pluginName, contentModule = null, isTestPlugin = isTest))

    fun recordModule(moduleId: PluginModuleId) {
      if (moduleId.namespace == PluginModuleId.DEFAULT_NAMESPACE) {
        target.computeIfAbsent(PluginId(moduleId.name)) { LinkedHashSet() }
          .add(PluginDescriptorIdConflictError.DescriptorOwner(pluginName, contentModule = moduleId.contentName(), isTestPlugin = isTest))
      }
    }

    plugin.containsContentWithNamespace { module, _ -> recordModule(module.moduleId()) }
    plugin.containsContentWithNamespaceTest { module, _ -> recordModule(module.moduleId()) }
  }

  productV.bundles { plugin -> recordPlugin(plugin, isTest = false, target = productionOwners) }

  val duplicates = LinkedHashMap<PluginId, LinkedHashSet<PluginDescriptorIdConflictError.DescriptorOwner>>()
  productV.bundlesTest { testPlugin ->
    val testOwners = LinkedHashMap<PluginId, LinkedHashSet<PluginDescriptorIdConflictError.DescriptorOwner>>()
    recordPlugin(testPlugin, isTest = true, target = testOwners)
    if (testOwners.isEmpty()) {
      return@bundlesTest
    }

    // Plugins this test plugin drags into the run, even though the product does not bundle them.
    val ownersForTestPlugin = LinkedHashMap(productionOwners)
    val additionalBundles = testPlugin.pluginIdOrNull?.let { additionalBundlesByTestPluginId[it] }
    if (additionalBundles != null) {
      val additionalOwners = LinkedHashMap<PluginId, LinkedHashSet<PluginDescriptorIdConflictError.DescriptorOwner>>()
      for (targetName in additionalBundles) {
        val additionalPlugin = plugin(targetName.value) ?: continue
        recordPlugin(additionalPlugin, isTest = false, target = additionalOwners)
      }
      for ((descriptorId, owners) in additionalOwners) {
        ownersForTestPlugin.computeIfAbsent(descriptorId) { LinkedHashSet() }.addAll(owners)
      }
    }

    for ((descriptorId, testOwnersForId) in testOwners) {
      val prodOwners = ownersForTestPlugin[descriptorId] ?: continue
      val combined = duplicates.computeIfAbsent(descriptorId) { LinkedHashSet() }
      combined.addAll(prodOwners)
      combined.addAll(testOwnersForId)
    }
  }

  if (duplicates.isEmpty()) {
    return emptyList()
  }

  val sorted = duplicates.mapValues { (_, owners) ->
    owners.sortedWith(
      compareBy<PluginDescriptorIdConflictError.DescriptorOwner> { it.pluginName.value }
        .thenBy { it.contentModule?.value ?: "" }
    )
  }
  return listOf(PluginDescriptorIdConflictError(context = productName, duplicates = sorted))
}
