// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.validator

import org.jetbrains.intellij.build.productLayout.model.error.EmbeddedContentModuleDependencyError
import org.jetbrains.intellij.build.productLayout.model.error.EmbeddedContentModuleDependencyIssue
import org.jetbrains.intellij.build.productLayout.model.error.EmbeddedContentModuleSource
import org.jetbrains.intellij.build.productLayout.pipeline.ComputeContext
import org.jetbrains.intellij.build.productLayout.pipeline.DataSlot
import org.jetbrains.intellij.build.productLayout.pipeline.NodeIds
import org.jetbrains.intellij.build.productLayout.pipeline.PipelineNode
import org.jetbrains.intellij.build.productLayout.pipeline.Slots
import org.jetbrains.intellij.build.productLayout.traversal.analyzeEmbeddedDependencyClosure

/**
 * Ensures product/module-set embedded modules do not depend on content exported by bundled plugin wrappers.
 */
internal object EmbeddedContentModuleDependencyValidator : PipelineNode {
  override val id get() = NodeIds.EMBEDDED_CONTENT_MODULE_DEPENDENCY_VALIDATION
  override val requires: Set<DataSlot<*>> get() = setOf(Slots.CONTENT_MODULE_PLAN)

  override suspend fun execute(ctx: ComputeContext) {
    val result = analyzeEmbeddedDependencyClosure(ctx.model.pluginGraph, pluginSourceOnly = true)
    for ((productName, violations) in result.violations.groupBy { it.product }) {
      ctx.emitError(EmbeddedContentModuleDependencyError(
        context = productName,
        violations = violations.map { violation ->
          EmbeddedContentModuleDependencyIssue(
            sourceModule = violation.sourceModule,
            dependency = violation.dependency,
            dependencyPath = violation.dependencyPath,
            dependencySources = violation.dependencySources.map { source ->
              EmbeddedContentModuleSource(
                kind = source.kind,
                name = source.name,
                loading = source.loading,
              )
            },
          )
        },
      ))
    }
  }
}
