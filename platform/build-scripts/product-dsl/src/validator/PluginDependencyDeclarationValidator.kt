// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.productLayout.validator

import com.intellij.platform.pluginGraph.TargetName
import org.jetbrains.intellij.build.productLayout.model.error.DuplicatePluginDependencyDeclarationError
import org.jetbrains.intellij.build.productLayout.pipeline.ComputeContext
import org.jetbrains.intellij.build.productLayout.pipeline.DataSlot
import org.jetbrains.intellij.build.productLayout.pipeline.NodeIds
import org.jetbrains.intellij.build.productLayout.pipeline.PipelineNode
import org.jetbrains.intellij.build.productLayout.pipeline.Slots

/**
 * Plugin dependency declaration duplicate validation.
 *
 * Purpose: Detect dependencies declared in both legacy `<depends>` and modern `<dependencies><plugin/>` forms.
 * Inputs: plugin graph dependency edges with legacy/modern flags.
 * Output: `DuplicatePluginDependencyDeclarationError`.
 * Auto-fix: none.
 *
 * Glossary: docs/validators/README.md.
 * Spec: docs/validators/plugin-dependency-declaration.md.
 */
internal object PluginDependencyDeclarationValidator : PipelineNode {
  override val id get() = NodeIds.PLUGIN_DEPENDENCY_DECLARATION_VALIDATION

  override val requires: Set<DataSlot<*>> get() = setOf(Slots.PLUGIN_DEPENDENCY_PLAN)

  override suspend fun execute(ctx: ComputeContext) {
    val plans = ctx.get(Slots.PLUGIN_DEPENDENCY_PLAN).plans
    for (plan in plans) {
      if (plan.duplicateDeclarationPluginIds.isEmpty()) continue
      val pluginName = TargetName(plan.pluginContentModuleName.value)
      ctx.emitError(
        DuplicatePluginDependencyDeclarationError(
          context = pluginName.value,
          pluginName = pluginName,
          duplicatePluginIds = plan.duplicateDeclarationPluginIds,
        )
      )
    }
  }
}
