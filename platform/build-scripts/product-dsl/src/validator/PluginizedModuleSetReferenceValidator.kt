// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.build.productLayout.validator

import org.jetbrains.intellij.build.productLayout.ModuleSet
import org.jetbrains.intellij.build.productLayout.ProductModulesContentSpec
import org.jetbrains.intellij.build.productLayout.model.error.PluginizedModuleSetReferenceError
import org.jetbrains.intellij.build.productLayout.pipeline.ComputeContext
import org.jetbrains.intellij.build.productLayout.pipeline.NodeIds
import org.jetbrains.intellij.build.productLayout.pipeline.PipelineNode

/**
 * Validates that pluginized module sets are not referenced as regular module sets.
 *
 * Spec: docs/validators/pluginized-module-set-reference.md.
 */
internal object PluginizedModuleSetReferenceValidator : PipelineNode {
  override val id get() = NodeIds.PLUGINIZED_MODULE_SET_REFERENCE_VALIDATION

  override suspend fun execute(ctx: ComputeContext) {
    val discovery = ctx.model.discovery

    emitProductErrors(ctx, discovery.products.map { it.name to it.spec })
    emitProductErrors(ctx, discovery.testProductSpecs)

    for (moduleSet in discovery.allModuleSets) {
      if (moduleSet.pluginSpec != null) {
        continue
      }
      for (pluginizedSet in collectNestedPluginizedSetNames(moduleSet)) {
        ctx.emitError(
          PluginizedModuleSetReferenceError(
            context = moduleSet.name,
            pluginizedModuleSetName = pluginizedSet,
            ownerKind = PluginizedModuleSetReferenceError.OwnerKind.MODULE_SET,
          )
        )
      }
    }
  }
}

private fun emitProductErrors(
  ctx: ComputeContext,
  products: List<Pair<String, ProductModulesContentSpec?>>,
) {
  for ((productName, spec) in products) {
    if (spec == null) {
      continue
    }
    val pluginizedSetNames = LinkedHashSet<String>()
    for (moduleSetWithOverrides in spec.moduleSets) {
      val moduleSet = moduleSetWithOverrides.moduleSet
      if (moduleSet.pluginSpec != null) {
        pluginizedSetNames.add(moduleSet.name)
      }
    }
    for (pluginizedSetName in pluginizedSetNames) {
      ctx.emitError(
        PluginizedModuleSetReferenceError(
          context = productName,
          pluginizedModuleSetName = pluginizedSetName,
          ownerKind = PluginizedModuleSetReferenceError.OwnerKind.PRODUCT,
        )
      )
    }
  }
}

private fun collectNestedPluginizedSetNames(root: ModuleSet): Set<String> {
  val pluginizedSetNames = LinkedHashSet<String>()
  val visited = HashSet<String>()

  fun visit(moduleSet: ModuleSet) {
    if (!visited.add(moduleSet.name)) {
      return
    }
    for (nestedSet in moduleSet.nestedSets) {
      if (nestedSet.pluginSpec != null) {
        pluginizedSetNames.add(nestedSet.name)
      }
      else {
        visit(nestedSet)
      }
    }
  }

  visit(root)
  return pluginizedSetNames
}
