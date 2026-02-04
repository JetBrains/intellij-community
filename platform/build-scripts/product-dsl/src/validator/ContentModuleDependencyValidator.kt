// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.productLayout.validator

import com.intellij.platform.pluginGraph.ContentModuleNode
import com.intellij.platform.pluginGraph.PluginGraph
import com.intellij.platform.pluginGraph.ProductNode
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.intellij.build.productLayout.model.error.MissingDependenciesError
import org.jetbrains.intellij.build.productLayout.model.error.ValidationError
import org.jetbrains.intellij.build.productLayout.pipeline.ComputeContext
import org.jetbrains.intellij.build.productLayout.pipeline.DataSlot
import org.jetbrains.intellij.build.productLayout.pipeline.NodeIds
import org.jetbrains.intellij.build.productLayout.pipeline.PipelineNode
import org.jetbrains.intellij.build.productLayout.pipeline.Slots

/**
 * Content module dependency validation for bundled plugins.
 *
 * Purpose: Ensure dependencies of plugin content modules resolve per-product and globally for non-critical modules.
 * Inputs: `Slots.CONTENT_MODULE` (ContentModuleDependencyGenerator), plugin graph, product allowMissing dependencies.
 * Output: `MissingDependenciesError`.
 * Auto-fix: none.
 *
 * Glossary: docs/validators/README.md.
 * Spec: docs/validators/content-module-dependency.md.
 */
internal object ContentModuleDependencyValidator : PipelineNode {
  override val id get() = NodeIds.PLUGIN_CONTENT_MODULE_VALIDATION

  // Requires CONTENT_MODULE to ensure ContentModuleDependencyGenerator has populated the graph
  // with module dependency edges before this validation runs.
  override val requires: Set<DataSlot<*>> get() = setOf(Slots.CONTENT_MODULE)

  override suspend fun execute(ctx: ComputeContext) {
    val model = ctx.model
    coroutineScope {
      model.pluginGraph.query {
        products { product ->
          launch {
            ctx.emitErrors(validatePluginContentModulesForProduct(product, model.pluginGraph))
          }
        }
      }
    }
  }
}

/**
 * Validates plugin content modules in a single product using graph-first design.
 *
 * Type-safe traversals ensure compile-time correctness:
 * - `ProductNode.bundles()` returns `Sequence<PluginNode>`
 * - `PluginNode.content()` returns `Sequence<ModuleNode>`
 * - `ModuleNode.transitiveDeps()` returns `Sequence<ModuleNode>`
 *
 * Loading mode criticality is checked via the `isCritical()` DSL helper, which
 * reads packed loading mode directly from graph edges.
 */
private fun validatePluginContentModulesForProduct(
  productV: ProductNode,
  pluginGraph: PluginGraph,
): List<ValidationError> = pluginGraph.query {
  val productName = productV.name()

  // 1. Get content modules from plugins bundled by THIS product (GC-free DSL).
  // Traverse Product → Plugin → Module, deduplicated.
  val contentModules = HashSet<ContentModuleNode>()
  productV.bundles { plugin ->
    plugin.containsContent { module, _ ->
      contentModules.add(module)
    }
  }

  if (contentModules.isEmpty()) {
    return emptyList()
  }

  // 2. Find missing deps - query graph for criticality inline
  val missingDeps = collectMissingModuleDependencies(
    productId = productV.id,
    modulesToValidate = contentModules,
  )

  if (missingDeps.isEmpty()) {
    return emptyList()
  }

  return listOf(
    MissingDependenciesError(
      context = productName,
      missingModules = missingDeps,
      pluginGraph = pluginGraph,
      ruleName = "PluginContentModuleValidation",
    )
  )
}
