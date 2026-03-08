// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.build.productLayout.validator

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue
import org.jetbrains.intellij.build.productLayout.ModuleSet
import org.jetbrains.intellij.build.productLayout.collectPluginizedModuleSets
import org.jetbrains.intellij.build.productLayout.model.error.ModuleSetPluginizationError
import org.jetbrains.intellij.build.productLayout.model.error.DuplicateModuleSetPluginWrapperError
import org.jetbrains.intellij.build.productLayout.model.error.UltimateModuleSetMainModuleError
import org.jetbrains.intellij.build.productLayout.moduleSetPluginModuleName
import org.jetbrains.intellij.build.productLayout.pipeline.ComputeContext
import org.jetbrains.intellij.build.productLayout.pipeline.NodeIds
import org.jetbrains.intellij.build.productLayout.pipeline.PipelineNode

/**
 * Validates module sets materialized as plugins.
 *
 * Rules:
 * 1. Pluginized module sets cannot contain embedded modules (directly or transitively).
 * 2. Pluginized module sets cannot contain nested pluginized sets.
 */
internal object ModuleSetPluginizationValidator : PipelineNode {
  override val id get() = NodeIds.MODULE_SET_PLUGINIZATION_VALIDATION

  override suspend fun execute(ctx: ComputeContext) {
    val allModuleSets = ctx.model.discovery.allModuleSets
    for (moduleSet in allModuleSets) {
      if (moduleSet.pluginSpec == null) {
        continue
      }

      val embeddedModules = collectEmbeddedModules(moduleSet)
      val nestedPluginizedSets = collectNestedPluginizedSets(moduleSet)
      if (embeddedModules.isEmpty() && nestedPluginizedSets.isEmpty()) {
        continue
      }

      ctx.emitError(
        ModuleSetPluginizationError(
          context = moduleSet.name,
          embeddedModules = embeddedModules,
          nestedPluginizedSets = nestedPluginizedSets,
        )
      )
    }

    val communityWrapperModuleNames = collectPluginizedModuleSets(ctx.model.discovery.communityModuleSets)
      .mapTo(LinkedHashSet()) { moduleSetPluginModuleName(it.name).value }
    val duplicateWrapperModuleNames = collectPluginizedModuleSets(ctx.model.discovery.ultimateModuleSets)
      .map { moduleSetPluginModuleName(it.name).value }
      .filterTo(LinkedHashSet()) { it in communityWrapperModuleNames }
    for (moduleName in duplicateWrapperModuleNames.sorted()) {
      ctx.emitError(DuplicateModuleSetPluginWrapperError(context = moduleName))
    }

    for (moduleSet in collectPluginizedModuleSets(ctx.model.discovery.ultimateModuleSets)) {
      if (requireNotNull(moduleSet.pluginSpec).addToMainModule) {
        ctx.emitError(UltimateModuleSetMainModuleError(context = moduleSet.name))
      }
    }
  }
}

private fun collectEmbeddedModules(root: ModuleSet): Set<ContentModuleName> {
  val embeddedModules = LinkedHashSet<ContentModuleName>()
  val visited = HashSet<String>()

  fun visit(moduleSet: ModuleSet) {
    if (!visited.add(moduleSet.name)) {
      return
    }
    for (module in moduleSet.modules) {
      if (module.loading == ModuleLoadingRuleValue.EMBEDDED) {
        embeddedModules.add(module.name)
      }
    }
    for (nestedSet in moduleSet.nestedSets) {
      visit(nestedSet)
    }
  }

  visit(root)
  return embeddedModules
}

private fun collectNestedPluginizedSets(root: ModuleSet): Set<String> {
  val pluginizedSets = LinkedHashSet<String>()
  val visited = HashSet<String>()

  fun visit(moduleSet: ModuleSet, isRoot: Boolean) {
    if (!visited.add(moduleSet.name)) {
      return
    }
    if (!isRoot && moduleSet.pluginSpec != null) {
      pluginizedSets.add(moduleSet.name)
      return
    }
    for (nestedSet in moduleSet.nestedSets) {
      visit(nestedSet, isRoot = false)
    }
  }

  visit(root, isRoot = true)
  return pluginizedSets
}
