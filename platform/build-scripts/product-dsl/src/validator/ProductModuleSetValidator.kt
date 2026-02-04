// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.productLayout.validator

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.GraphScope
import com.intellij.platform.pluginGraph.ProductNode
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.intellij.build.productLayout.model.error.DuplicateModulesError
import org.jetbrains.intellij.build.productLayout.model.error.MissingDependenciesError
import org.jetbrains.intellij.build.productLayout.pipeline.ComputeContext
import org.jetbrains.intellij.build.productLayout.pipeline.GenerationModel
import org.jetbrains.intellij.build.productLayout.pipeline.NodeIds
import org.jetbrains.intellij.build.productLayout.pipeline.PipelineNode

/**
 * Product module set validation.
 *
 * Purpose: Detect duplicate modules and missing transitive deps for product module sets and product content.
 * Inputs: plugin graph, product allowMissing dependencies.
 * Output: `DuplicateModulesError`, `MissingDependenciesError`.
 * Auto-fix: none.
 *
 * Glossary: docs/validators/README.md.
 * Spec: docs/validators/product-module-set.md.
 */
internal object ProductModuleSetValidator : PipelineNode {
  override val id get() = NodeIds.PRODUCT_MODULE_SET_VALIDATION

  override suspend fun execute(ctx: ComputeContext) {
    val model = ctx.model
    coroutineScope {
      model.pluginGraph.query {
        products { product ->
          launch {
            model.pluginGraph.query {
              validateProductModuleSet(product, ctx, model)
            }
          }
        }
      }
    }
  }
}

private fun GraphScope.validateProductModuleSet(
  product: ProductNode,
  ctx: ComputeContext,
  model: GenerationModel,
) {
  val productName = product.name()
  val modules = collectProductModules(product)
  val duplicateModules = modules.duplicateModules

  // Emit structural errors
  if (duplicateModules.isNotEmpty()) {
    val sortedDuplicates = sortedMapOf<ContentModuleName, Int>(compareBy { it.value })
    duplicateModules.forEach { name, count -> sortedDuplicates.put(name, count) }
    ctx.emitError(DuplicateModulesError(context = productName, duplicates = sortedDuplicates))
  }

  if (modules.modulesToValidate.isEmpty()) {
    return
  }

  // === PHASE 2: Check transitive deps ===
  val missingDeps = collectMissingModuleDependencies(
    productId = product.id,
    modulesToValidate = modules.modulesToValidate,
  )

  if (missingDeps.isNotEmpty()) {
    ctx.emitError(
      MissingDependenciesError(
        context = productName,
        missingModules = missingDeps,
        pluginGraph = model.pluginGraph,
        ruleName = "ProductModuleSetValidation",
      )
    )
  }
}
