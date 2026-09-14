// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.productLayout.validator

import com.intellij.platform.pluginGraph.ContentModuleName
import org.jetbrains.intellij.build.productLayout.model.error.RdClientModuleLoadingError
import org.jetbrains.intellij.build.productLayout.pipeline.ComputeContext
import org.jetbrains.intellij.build.productLayout.pipeline.DataSlot
import org.jetbrains.intellij.build.productLayout.pipeline.NodeIds
import org.jetbrains.intellij.build.productLayout.pipeline.PipelineNode
import org.jetbrains.intellij.build.productLayout.pipeline.Slots
import org.jetbrains.intellij.build.productLayout.traversal.ProductModuleLoading

/** Checks RD client activation in each discovered production product. */
internal object RdClientModuleLoadingValidator : PipelineNode {
  override val id get() = NodeIds.RD_CLIENT_MODULE_LOADING_VALIDATION
  override val requires: Set<DataSlot<*>> get() = setOf(Slots.CONTENT_MODULE_PLAN, Slots.PLUGIN_DEPENDENCY_PLAN)

  override fun execute(ctx: ComputeContext) {
    val model = ctx.model
    val loading = ProductModuleLoading(
      graph = model.pluginGraph,
      contentPlans = ctx.get(Slots.CONTENT_MODULE_PLAN),
      pluginPlans = ctx.get(Slots.PLUGIN_DEPENDENCY_PLAN),
      descriptorLookup = { model.descriptorCache.getOrAnalyze(it.value) },
      pluginLookup = { model.pluginContentCache.getOrExtract(it) },
    )
    for (product in model.discovery.products.sortedBy { it.name }) {
      val result = loading.analyze(product.name, product.spec, product.modularLoaderPluginModules)
      val allowed = product.name == "Rider" || product.name == "CLion" || product.name.endsWith("JetBrainsClient")
      val unexpected = if (allowed) emptyMap() else result.activationPaths.filterKeys { it.value.startsWith(RD_CLIENT_PREFIX) }
      val missing = if (allowed) {
        REQUIRED_MODULES.filterNot { it in result.activationPaths }.associateWith {
          result.exclusions.get(it) ?: "The module is absent from this product."
        }
      }
      else {
        emptyMap()
      }
      if (unexpected.isNotEmpty() || missing.isNotEmpty()) {
        ctx.emitError(RdClientModuleLoadingError(product.name, unexpected, missing))
      }
    }
  }
}

private const val RD_CLIENT_PREFIX = "intellij.rd.client"
private val REQUIRED_MODULES = listOf(
  ContentModuleName(RD_CLIENT_PREFIX),
  ContentModuleName("$RD_CLIENT_PREFIX.base"),
)
