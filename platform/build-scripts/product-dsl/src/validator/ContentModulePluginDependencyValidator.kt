// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:Suppress("GrazieInspection", "ReplaceGetOrSet")

package org.jetbrains.intellij.build.productLayout.validator

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.PluginGraph
import com.intellij.platform.pluginGraph.PluginId
import org.jetbrains.intellij.build.productLayout.deps.ContentModuleDependencyPlan
import org.jetbrains.intellij.build.productLayout.model.error.MissingContentModulePluginDependencyError
import org.jetbrains.intellij.build.productLayout.model.error.ValidationError
import org.jetbrains.intellij.build.productLayout.pipeline.ComputeContext
import org.jetbrains.intellij.build.productLayout.pipeline.DataSlot
import org.jetbrains.intellij.build.productLayout.pipeline.NodeIds
import org.jetbrains.intellij.build.productLayout.pipeline.PipelineNode
import org.jetbrains.intellij.build.productLayout.pipeline.Slots

/**
 * Content module plugin dependency validation.
 *
 * Purpose: Ensure content module XMLs declare plugin dependencies for main plugin modules from IML.
 * Inputs: `Slots.CONTENT_MODULE_PLAN`, plugin graph, suppression config.
 * Output: `MissingContentModulePluginDependencyError`.
 * Auto-fix: none.
 *
 * Glossary: docs/validators/README.md.
 * Spec: docs/validators/plugin-content-dependency.md.
 */
internal object ContentModulePluginDependencyValidator : PipelineNode {
  override val id get() = NodeIds.CONTENT_MODULE_PLUGIN_DEPENDENCY_VALIDATION

  override val requires: Set<DataSlot<*>> get() = setOf(Slots.CONTENT_MODULE_PLAN)

  override suspend fun execute(ctx: ComputeContext) {
    val model = ctx.model
    ctx.emitErrors(
      validateContentModulePluginDependencies(
        contentModulePlans = ctx.get(Slots.CONTENT_MODULE_PLAN).plans,
        pluginGraph = model.pluginGraph,
        allowedMissing = model.suppressionConfig.getAllowedMissingPluginsMap(),
      )
    )
  }
}

/**
 * Validates that content module XML files declare all plugin dependencies from IML.
 *
 * Catches the case where a content module has a compile dependency on a main plugin module
 * in its `.iml` file, but the generated XML doesn't have the corresponding `<plugin id="..."/>` dependency.
 *
 * @param contentModulePlans Results from content module dependency planning
 * @param pluginGraph Graph used to resolve containing plugins for modules
 * @param allowedMissing Map of content module name to set of allowed missing plugin IDs
 * @return List of validation errors for modules with missing plugin deps
 */
private fun validateContentModulePluginDependencies(
  contentModulePlans: List<ContentModuleDependencyPlan>,
  pluginGraph: PluginGraph,
  allowedMissing: Map<ContentModuleName, Set<PluginId>> = emptyMap(),
): List<ValidationError> {
  if (contentModulePlans.isEmpty()) {
    return emptyList()
  }

  return pluginGraph.query {
    val errors = ArrayList<ValidationError>()

    fun collectContainingPluginIds(moduleName: ContentModuleName): Set<PluginId> {
      val moduleNode = contentModule(moduleName) ?: return emptySet()
      val ids = HashSet<PluginId>()
      moduleNode.owningPlugins { plugin ->
        val pluginId = plugin.pluginIdOrNull ?: return@owningPlugins
        ids.add(pluginId)
      }
      return ids
    }

    for (plan in contentModulePlans) {
      val moduleName = plan.contentModuleName

      val writtenPluginDeps = plan.writtenPluginDependencies.toHashSet()
      val allJpsPluginDeps = plan.allJpsPluginDependencies

      val allowedForModule = allowedMissing.get(moduleName) ?: emptySet()
      val candidateMissing = allJpsPluginDeps - writtenPluginDeps - allowedForModule
      if (candidateMissing.isEmpty()) {
        continue
      }

      val containingPluginIdsForModule = collectContainingPluginIds(moduleName)

      // Exclude ALL containing plugins - content modules have an implicit dependency on them
      val missingPluginDeps = candidateMissing - containingPluginIdsForModule
      if (missingPluginDeps.isNotEmpty()) {
        errors.add(MissingContentModulePluginDependencyError(
          context = plan.contentModuleName.value,
          contentModuleName = plan.contentModuleName,
          missingPluginIds = missingPluginDeps,
        ))
      }
    }

    errors
  }
}
