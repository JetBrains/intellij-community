// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "GrazieStyle")

package org.jetbrains.intellij.build.productLayout.validator

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.ModuleSetNode
import com.intellij.platform.pluginGraph.PluginGraph
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.intellij.build.productLayout.model.error.SelfContainedValidationError
import org.jetbrains.intellij.build.productLayout.pipeline.ComputeContext
import org.jetbrains.intellij.build.productLayout.pipeline.DataSlot
import org.jetbrains.intellij.build.productLayout.pipeline.NodeIds
import org.jetbrains.intellij.build.productLayout.pipeline.PipelineNode
import org.jetbrains.intellij.build.productLayout.pipeline.Slots

/**
 * Self-contained module set validation.
 *
 * Purpose: Ensure selfContained module sets have all transitive deps within the set hierarchy.
 * Inputs: `Slots.CONTENT_MODULE_PLAN`, plugin graph.
 * Output: `SelfContainedValidationError`.
 * Auto-fix: none.
 *
 * Glossary: docs/validators/README.md.
 * Spec: docs/validators/self-contained-module-set.md.
 */
internal object SelfContainedModuleSetValidator : PipelineNode {
  override val id get() = NodeIds.SELF_CONTAINED_VALIDATION
  override val requires: Set<DataSlot<*>> get() = setOf(Slots.CONTENT_MODULE_PLAN)

  override suspend fun execute(ctx: ComputeContext) {
    val model = ctx.model
    val pluginGraph = model.pluginGraph

    // Validate each self-contained set in parallel
    coroutineScope {
      pluginGraph.query {
        moduleSets { moduleSet ->
          if (!moduleSet.selfContained) {
            return@moduleSets
          }
          launch {
            validateSelfContainedModuleSet(moduleSet, pluginGraph)?.let { ctx.emitError(it) }
          }
        }
      }
    }
  }
}

/**
 * Validates a single self-contained module set.
 *
 * @return validation error if missing dependencies found, null otherwise
 */
private fun validateSelfContainedModuleSet(
  moduleSetNode: ModuleSetNode,
  pluginGraph: PluginGraph,
): SelfContainedValidationError? {
  return pluginGraph.query {
    val moduleSetName = moduleSetNode.name()

    // Check deps - use graph lookup instead of string set membership
    val missingDeps = HashMap<ContentModuleName, MutableSet<ContentModuleName>>()
    moduleSetNode.modulesRecursive {
      val moduleName = it.name()
      it.transitiveDeps { dep ->
        // Check if dep is contained by any module set in our hierarchy
        if (!moduleSetNode.containsModuleRecursive(dep)) {
          missingDeps.computeIfAbsent(dep.name()) { HashSet() }.add(moduleName)
        }
      }
    }

    if (missingDeps.isNotEmpty()) {
      SelfContainedValidationError(
        context = moduleSetName,
        missingDependencies = missingDeps,
      )
    }
    else {
      null
    }
  }
}
