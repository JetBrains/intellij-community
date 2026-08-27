// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.validator

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.PluginGraph
import com.intellij.platform.pluginGraph.PluginId
import com.intellij.platform.pluginGraph.ProductNode
import org.jetbrains.intellij.build.productLayout.model.error.ContentModuleCopyConflictError
import org.jetbrains.intellij.build.productLayout.model.error.ValidationError
import org.jetbrains.intellij.build.productLayout.pipeline.ComputeContext
import org.jetbrains.intellij.build.productLayout.pipeline.DataSlot
import org.jetbrains.intellij.build.productLayout.pipeline.NodeIds
import org.jetbrains.intellij.build.productLayout.pipeline.PipelineNode
import org.jetbrains.intellij.build.productLayout.pipeline.Slots
import org.jetbrains.intellij.build.productLayout.traversal.analyzeContentModuleCopyConflicts

/**
 * Reachable copy conflict validation for a content module name.
 *
 * Purpose: Detect a content module that reaches two embedded copies of one content module name.
 * Two bundled plugins can each declare the same content module name in a namespace-less `<content>` block.
 * Each copy then gets an implicit per-plugin namespace, so the runtime reports no ID conflict.
 * A module that reaches both copies loads the same classes from two classloaders.
 * The result is a `LinkageError` at runtime. See https://youtrack.jetbrains.com/issue/QD-15883.
 *
 * An isolated private copy stays legal. The plugin model supports a private library copy per plugin.
 * This validator reports a copy only when a content module can reach two of them.
 * The validator reads an embedded declaration only. Read [analyzeContentModuleCopyConflicts] for the reason.
 *
 * One error covers one duplicated name in one product. The node itself drops a name that
 * `contentModuleCopyConflicts` in `suppressions.json` lists, so the grain is per name and a unit test sees it.
 * A name that the config does not list still fails the generator.
 *
 * Inputs: `Slots.CONTENT_MODULE_PLAN` for the descriptor plugin dependencies, plugin graph content and
 * dependency edges, and `model.suppressionConfig` for the allowed names.
 * Output: `ContentModuleCopyConflictError`, one per duplicated name per product.
 * Auto-fix: none.
 *
 * Glossary: docs/validators/README.md.
 * Spec: docs/validators/content-module-copy-conflict.md.
 */
internal object ContentModuleCopyConflictValidator : PipelineNode {
  override val id get() = NodeIds.CONTENT_MODULE_COPY_CONFLICT_VALIDATION

  // CONTENT_MODULE_PLAN carries the descriptor plugin dependencies. It also guarantees that
  // ContentModuleDependencyPlanner has published the module dependency edges of the graph.
  override val requires: Set<DataSlot<*>> get() = setOf(Slots.CONTENT_MODULE_PLAN)

  override suspend fun execute(ctx: ComputeContext) {
    val plan = ctx.get(Slots.CONTENT_MODULE_PLAN)
    val modulePluginDependencies = plan.plansByModule.mapValues { it.value.writtenPluginDependencies }
    val pluginGraph = ctx.model.pluginGraph
    // The pipeline filter on `suppressionKey` runs outside the node, so a unit test cannot see it.
    // The node therefore applies the same config itself, which also gives the per-name grain.
    val allowedNames = ctx.model.suppressionConfig.contentModuleCopyConflicts.keys
    ctx.emitErrorsPerProduct(pluginGraph) { product ->
      validateContentModuleCopiesForProduct(product, pluginGraph, modulePluginDependencies, allowedNames)
    }
  }
}

private fun validateContentModuleCopiesForProduct(
  product: ProductNode,
  pluginGraph: PluginGraph,
  modulePluginDependencies: Map<ContentModuleName, List<PluginId>>,
  allowedNames: Set<ContentModuleName>,
): List<ValidationError> = pluginGraph.query {
  val conflicts = analyzeContentModuleCopyConflicts(product, modulePluginDependencies)
  if (conflicts.isEmpty()) {
    return emptyList()
  }

  // One error per duplicated name, because the config allows one name at a time.
  val productName = product.name()
  return conflicts.groupBy { it.duplicatedModule }.entries
    .filter { it.key !in allowedNames }
    .sortedBy { it.key.value }
    .map { (duplicatedModule, group) ->
      ContentModuleCopyConflictError(context = productName, duplicatedModule = duplicatedModule, conflicts = group)
    }
}
