// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.validator

import org.jetbrains.intellij.build.productLayout.model.error.UnusedSharedLibraryModuleError
import org.jetbrains.intellij.build.productLayout.pipeline.ComputeContext
import org.jetbrains.intellij.build.productLayout.pipeline.DataSlot
import org.jetbrains.intellij.build.productLayout.pipeline.NodeIds
import org.jetbrains.intellij.build.productLayout.pipeline.PipelineNode
import org.jetbrains.intellij.build.productLayout.pipeline.Slots
import org.jetbrains.intellij.build.productLayout.traversal.analyzeUnusedSharedLibraryModules

/**
 * Ensures ordinary (non-embedded) library module-set content has at least one consumer.
 *
 * Products shipping a library nothing depends on is dead weight; the over-shipping case (a library present in
 * products where none of its consumers is) is reported through the analysis JSON instead of failing the build.
 */
internal object UnusedSharedLibraryModuleValidator : PipelineNode {
  override val id get() = NodeIds.UNUSED_SHARED_LIBRARY_MODULE_VALIDATION
  override val requires: Set<DataSlot<*>> get() = setOf(Slots.CONTENT_MODULE_PLAN)

  override suspend fun execute(ctx: ComputeContext) {
    val result = analyzeUnusedSharedLibraryModules(ctx.model.pluginGraph)
    if (result.violations.isNotEmpty()) {
      ctx.emitError(UnusedSharedLibraryModuleError(
        context = "module sets",
        violations = result.violations,
      ))
    }
  }
}
