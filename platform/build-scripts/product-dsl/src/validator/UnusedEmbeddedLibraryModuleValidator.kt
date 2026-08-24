// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.validator

import org.jetbrains.intellij.build.productLayout.model.error.UnusedEmbeddedLibraryModuleError
import org.jetbrains.intellij.build.productLayout.pipeline.ComputeContext
import org.jetbrains.intellij.build.productLayout.pipeline.DataSlot
import org.jetbrains.intellij.build.productLayout.pipeline.NodeIds
import org.jetbrains.intellij.build.productLayout.pipeline.PipelineNode
import org.jetbrains.intellij.build.productLayout.pipeline.Slots
import org.jetbrains.intellij.build.productLayout.traversal.analyzeUnusedEmbeddedLibraryModules

/**
 * Ensures embedded library modules are required by embedded platform content in at least one product.
 */
internal object UnusedEmbeddedLibraryModuleValidator : PipelineNode {
  override val id get() = NodeIds.UNUSED_EMBEDDED_LIBRARY_MODULE_VALIDATION
  override val requires: Set<DataSlot<*>> get() = setOf(Slots.CONTENT_MODULE_PLAN)

  override suspend fun execute(ctx: ComputeContext) {
    val productSpecsByName = ctx.model.discovery.products.mapNotNull { product ->
      product.spec?.let { product.name to it }
    }.toMap()
    val result = analyzeUnusedEmbeddedLibraryModules(ctx.model.pluginGraph, productSpecsByName)
    if (result.violations.isNotEmpty()) {
      ctx.emitError(UnusedEmbeddedLibraryModuleError(
        context = "module sets",
        violations = result.violations,
      ))
    }
  }
}
