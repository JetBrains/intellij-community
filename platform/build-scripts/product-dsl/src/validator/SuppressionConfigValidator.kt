// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.productLayout.validator

import com.intellij.platform.pluginGraph.NODE_CONTENT_MODULE
import com.intellij.platform.pluginGraph.TargetName
import org.jetbrains.intellij.build.productLayout.model.error.InvalidSuppressionConfigKeyError
import org.jetbrains.intellij.build.productLayout.pipeline.ComputeContext
import org.jetbrains.intellij.build.productLayout.pipeline.NodeIds
import org.jetbrains.intellij.build.productLayout.pipeline.PipelineNode

/**
 * Suppression config key validation.
 *
 * Purpose: Ensure suppressions.json keys reference existing content modules or plugins.
 * Inputs: suppressionConfig, plugin graph, updateSuppressions flag.
 * Output: `InvalidSuppressionConfigKeyError`.
 * Auto-fix: none (skipped when updateSuppressions is enabled).
 *
 * Glossary: docs/validators/README.md.
 * Spec: docs/validators/suppression-config.md.
 */
internal object SuppressionConfigValidator : PipelineNode {
  override val id get() = NodeIds.SUPPRESSION_CONFIG_VALIDATION

  override suspend fun execute(ctx: ComputeContext) {
    val model = ctx.model
    // Skip validation when updating suppressions - invalid keys are auto-removed
    if (model.updateSuppressions) {
      return
    }

    val moduleKeyStrings = model.suppressionConfig.contentModules.keys.mapTo(HashSet()) { it.value }
    val pluginKeyStrings = model.suppressionConfig.plugins.keys.mapTo(HashSet()) { it.value }
    if (moduleKeyStrings.isEmpty() && pluginKeyStrings.isEmpty()) {
      return
    }

    // Find existing modules and plugins using index lookups
    val pluginGraph = model.pluginGraph
    val (existingModules, existingPlugins) = pluginGraph.query {
      val modules = HashSet<String>()
      val plugins = HashSet<TargetName>()

      // Check modules
      for (moduleKey in moduleKeyStrings) {
        val nodeId = nodeId(moduleKey, NODE_CONTENT_MODULE)
        if (nodeId >= 0) {
          modules.add(name(nodeId))
        }
      }

      // Check plugins
      for (pluginKey in pluginKeyStrings) {
        plugin(pluginKey)?.let { plugins.add(it.name()) }
      }

      modules to plugins
    }

    val invalidContentModuleKeys = model.suppressionConfig.contentModules.keys
      .filterNotTo(HashSet()) { it.value in existingModules }
    val invalidPluginKeys = model.suppressionConfig.plugins.keys
      .filterNotTo(HashSet()) { TargetName(it.value) in existingPlugins }
    if (invalidContentModuleKeys.isNotEmpty() || invalidPluginKeys.isNotEmpty()) {
      ctx.emitError(InvalidSuppressionConfigKeyError(
        context = "suppressions.json",
        invalidContentModuleKeys = invalidContentModuleKeys,
        invalidPluginKeys = invalidPluginKeys,
      ))
    }
  }
}
