// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.productLayout.validator

import com.intellij.platform.pluginGraph.PluginGraph
import com.intellij.platform.pluginGraph.PluginId
import kotlinx.coroutines.coroutineScope
import org.jetbrains.intellij.build.productLayout.debug
import org.jetbrains.intellij.build.productLayout.model.error.PluginDependencyNotBundledError
import org.jetbrains.intellij.build.productLayout.model.error.ValidationError
import org.jetbrains.intellij.build.productLayout.pipeline.ComputeContext
import org.jetbrains.intellij.build.productLayout.pipeline.DataSlot
import org.jetbrains.intellij.build.productLayout.pipeline.NodeIds
import org.jetbrains.intellij.build.productLayout.pipeline.PipelineNode

/**
 * Plugin-to-plugin dependency validation.
 *
 * Purpose: Ensure required plugin dependencies are bundled in the same products.
 * Inputs: plugin graph dependency and bundling edges.
 * Output: `PluginDependencyNotBundledError`.
 * Auto-fix: none.
 *
 * Glossary: docs/validators/README.md.
 * Spec: docs/validators/plugin-plugin-dependency.md.
 */
internal object PluginPluginDependencyValidator : PipelineNode {
  override val id get() = NodeIds.PLUGIN_PLUGIN_VALIDATION

  override val requires: Set<DataSlot<*>> get() = emptySet()

  override suspend fun execute(ctx: ComputeContext) {
    coroutineScope {
      val errors = validatePluginToPluginDependencies(ctx.model.pluginGraph)
      ctx.emitErrors(errors)
    }
  }
}

internal fun validatePluginToPluginDependencies(graph: PluginGraph): List<ValidationError> {
  val errors = mutableListOf<ValidationError>()

  data class DependencyTargetInfo(
    val bundledProducts: MutableSet<String> = LinkedHashSet(),
    var hasMainTarget: Boolean = false,
  )

  graph.query {
    plugins { plugin ->
      val pluginName = plugin.name()

      val bundledProducts = HashSet<String>()
      plugin.bundledByProducts { bundledProducts.add(it.name()) }

      val missingByProduct = LinkedHashMap<String, MutableSet<PluginId>>()
      val unresolvedDependencies = LinkedHashSet<PluginId>()
      val dependencyTargets = LinkedHashMap<PluginId, DependencyTargetInfo>()

      plugin.dependsOnPlugin { dep ->
        if (dep.isOptional) return@dependsOnPlugin

        val targetPlugin = dep.target()
        val targetPluginId = targetPlugin.pluginIdOrNull ?: return@dependsOnPlugin
        val info = dependencyTargets.computeIfAbsent(targetPluginId) { DependencyTargetInfo() }

        targetPlugin.bundledByProducts { info.bundledProducts.add(it.name()) }

        var hasMainTarget = false
        targetPlugin.mainTarget { _ -> hasMainTarget = true }
        if (hasMainTarget) {
          info.hasMainTarget = true
        }
      }

      for ((targetPluginId, info) in dependencyTargets) {
        if (!info.hasMainTarget) {
          if (info.bundledProducts.isEmpty()) {
            unresolvedDependencies.add(targetPluginId)
            continue
          }
          debug("aliasGraph") { "plugin=${pluginName.value} depends on alias=${targetPluginId.value} bundledIn=${info.bundledProducts}" }
        }

        if (bundledProducts.isEmpty()) {
          continue
        }

        for (product in bundledProducts) {
          if (product !in info.bundledProducts) {
            missingByProduct.computeIfAbsent(product) { LinkedHashSet() }.add(targetPluginId)
          }
        }
      }

      if (missingByProduct.isNotEmpty() || unresolvedDependencies.isNotEmpty()) {
        errors.add(PluginDependencyNotBundledError(
          context = pluginName.value,
          pluginName = pluginName,
          missingByProduct = missingByProduct,
          unresolvedDependencies = unresolvedDependencies,
        ))
      }
    }
  }

  return errors
}
