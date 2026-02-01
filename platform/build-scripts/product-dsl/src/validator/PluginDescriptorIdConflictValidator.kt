// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.validator

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.PluginGraph
import com.intellij.platform.pluginGraph.PluginId
import com.intellij.platform.pluginGraph.PluginNode
import com.intellij.platform.pluginGraph.ProductNode
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.intellij.build.productLayout.model.error.PluginDescriptorIdConflictError
import org.jetbrains.intellij.build.productLayout.model.error.ValidationError
import org.jetbrains.intellij.build.productLayout.pipeline.ComputeContext
import org.jetbrains.intellij.build.productLayout.pipeline.DataSlot
import org.jetbrains.intellij.build.productLayout.pipeline.NodeIds
import org.jetbrains.intellij.build.productLayout.pipeline.PipelineNode

// Keep in sync with ModelBuildingStage alias node naming.
private const val ALIAS_NODE_PREFIX = "__alias__:"

/**
 * Validates that test plugins do not declare descriptor IDs already provided by production plugins.
 */
internal object PluginDescriptorIdConflictValidator : PipelineNode {
  override val id get() = NodeIds.PLUGIN_DESCRIPTOR_ID_CONFLICT_VALIDATION
  override val requires: Set<DataSlot<*>> get() = emptySet()

  override suspend fun execute(ctx: ComputeContext) {
    val model = ctx.model
    coroutineScope {
      model.pluginGraph.query {
        products { product ->
          launch {
            ctx.emitErrors(validateDescriptorIdConflictsForProduct(product, model.pluginGraph))
          }
        }
      }
    }
  }
}

private fun validateDescriptorIdConflictsForProduct(
  productV: ProductNode,
  pluginGraph: PluginGraph,
): List<ValidationError> = pluginGraph.query {
  val productName = productV.name()
  val productionOwners = LinkedHashMap<PluginId, LinkedHashSet<PluginDescriptorIdConflictError.DescriptorOwner>>()
  val testOwners = LinkedHashMap<PluginId, LinkedHashSet<PluginDescriptorIdConflictError.DescriptorOwner>>()

  fun recordPlugin(
    plugin: PluginNode,
    isTest: Boolean,
    target: MutableMap<PluginId, LinkedHashSet<PluginDescriptorIdConflictError.DescriptorOwner>>,
  ) {
    val pluginName = plugin.name()
    if (pluginName.value.startsWith(ALIAS_NODE_PREFIX)) {
      return
    }

    val pluginIdValue = plugin.pluginIdOrNull ?: return
    target.computeIfAbsent(pluginIdValue) { LinkedHashSet() }
      .add(PluginDescriptorIdConflictError.DescriptorOwner(pluginName, contentModule = null, isTestPlugin = isTest))

    fun recordModule(moduleName: ContentModuleName) {
      target.computeIfAbsent(PluginId(moduleName.value)) { LinkedHashSet() }
        .add(PluginDescriptorIdConflictError.DescriptorOwner(pluginName, contentModule = moduleName, isTestPlugin = isTest))
    }

    plugin.containsContent { module, _ -> recordModule(module.contentName()) }
    plugin.containsContentTest { module, _ -> recordModule(module.contentName()) }
  }

  productV.bundles { plugin -> recordPlugin(plugin, isTest = false, target = productionOwners) }
  productV.bundlesTest { plugin -> recordPlugin(plugin, isTest = true, target = testOwners) }

  val duplicates = LinkedHashMap<PluginId, List<PluginDescriptorIdConflictError.DescriptorOwner>>()
  for ((descriptorId, prodOwners) in productionOwners) {
    val testOwnersForId = testOwners[descriptorId] ?: continue

    val combined = LinkedHashSet<PluginDescriptorIdConflictError.DescriptorOwner>(
      prodOwners.size + testOwnersForId.size
    )
    combined.addAll(prodOwners)
    combined.addAll(testOwnersForId)
    duplicates[descriptorId] = combined.sortedWith(
      compareBy<PluginDescriptorIdConflictError.DescriptorOwner> { it.pluginName.value }
        .thenBy { it.contentModule?.value ?: "" }
    )
  }

  if (duplicates.isEmpty()) {
    return emptyList()
  }

  return listOf(PluginDescriptorIdConflictError(context = productName, duplicates = duplicates))
}
