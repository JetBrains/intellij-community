// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.productLayout.validator

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.PluginGraph
import com.intellij.platform.pluginGraph.PluginNode
import com.intellij.platform.pluginGraph.ProductNode
import org.jetbrains.intellij.build.productLayout.model.error.DuplicatePluginContentModulesError
import org.jetbrains.intellij.build.productLayout.model.error.ValidationError
import org.jetbrains.intellij.build.productLayout.pipeline.ComputeContext
import org.jetbrains.intellij.build.productLayout.pipeline.DataSlot
import org.jetbrains.intellij.build.productLayout.pipeline.NodeIds
import org.jetbrains.intellij.build.productLayout.pipeline.PipelineNode

/**
 * Duplicate plugin content module validation.
 *
 * Purpose: Detect content modules declared by both production and test plugins in the same product.
 * Inputs: plugin graph product bundling and plugin content edges.
 * Output: `DuplicatePluginContentModulesError`.
 * Auto-fix: none.
 *
 * Glossary: docs/validators/README.md.
 * Spec: docs/validators/plugin-content-duplicates.md.
 */
internal object PluginContentDuplicatesValidator : PipelineNode {
  override val id get() = NodeIds.PLUGIN_CONTENT_DUPLICATE_VALIDATION
  override val requires: Set<DataSlot<*>> get() = emptySet()

  override suspend fun execute(ctx: ComputeContext) {
    val model = ctx.model
    ctx.emitErrorsPerProduct(model.pluginGraph) { product ->
      validateDuplicatePluginContentModulesForProduct(product, model.pluginGraph)
    }
  }
}

private fun validateDuplicatePluginContentModulesForProduct(
  productV: ProductNode,
  pluginGraph: PluginGraph,
): List<ValidationError> = pluginGraph.query {
  val productName = productV.name()
  val productionOwners = LinkedHashMap<ContentModuleName, LinkedHashSet<DuplicatePluginContentModulesError.PluginOwner>>()
  val testOwners = LinkedHashMap<ContentModuleName, LinkedHashSet<DuplicatePluginContentModulesError.PluginOwner>>()

  fun recordPluginModules(
    plugin: PluginNode,
    isTest: Boolean,
    target: MutableMap<ContentModuleName, LinkedHashSet<DuplicatePluginContentModulesError.PluginOwner>>,
  ) {
    val owner = DuplicatePluginContentModulesError.PluginOwner(plugin.name(), isTest)
    plugin.containsContent { module, _ -> target.getOrPut(module.contentName()) { LinkedHashSet() }.add(owner) }
    plugin.containsContentTest { module, _ -> target.getOrPut(module.contentName()) { LinkedHashSet() }.add(owner) }
  }

  productV.bundles { plugin -> recordPluginModules(plugin, isTest = false, target = productionOwners) }
  productV.bundlesTest { plugin -> recordPluginModules(plugin, isTest = true, target = testOwners) }

  val duplicates = LinkedHashMap<ContentModuleName, List<DuplicatePluginContentModulesError.PluginOwner>>()
  for ((moduleName, prodOwners) in productionOwners) {
    val testOwnersForModule = testOwners[moduleName] ?: continue

    val combined = LinkedHashSet<DuplicatePluginContentModulesError.PluginOwner>(
      prodOwners.size + testOwnersForModule.size
    )
    combined.addAll(prodOwners)
    combined.addAll(testOwnersForModule)
    duplicates[moduleName] = combined.sortedBy { it.pluginName.value }
  }

  if (duplicates.isEmpty()) {
    return emptyList()
  }

  return listOf(DuplicatePluginContentModulesError(context = productName, duplicates = duplicates))
}
