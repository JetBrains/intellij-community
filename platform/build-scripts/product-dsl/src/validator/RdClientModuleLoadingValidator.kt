// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.productLayout.validator

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.PluginId
import com.intellij.platform.pluginGraph.TargetName
import org.jetbrains.intellij.build.productLayout.TestPluginSpec
import org.jetbrains.intellij.build.productLayout.model.error.RdClientModuleLoadingError
import org.jetbrains.intellij.build.productLayout.pipeline.ComputeContext
import org.jetbrains.intellij.build.productLayout.pipeline.DataSlot
import org.jetbrains.intellij.build.productLayout.pipeline.NodeIds
import org.jetbrains.intellij.build.productLayout.pipeline.PipelineNode
import org.jetbrains.intellij.build.productLayout.pipeline.Slots
import org.jetbrains.intellij.build.productLayout.traversal.ProductModuleLoading

/** Checks RD client activation in products and the CLion test configurations. */
internal object RdClientModuleLoadingValidator : PipelineNode {
  override val id get() = NodeIds.RD_CLIENT_MODULE_LOADING_VALIDATION
  override val requires: Set<DataSlot<*>>
    get() = setOf(Slots.CONTENT_MODULE_PLAN, Slots.PLUGIN_DEPENDENCY_PLAN, Slots.TEST_PLUGIN_DEPENDENCY_PLAN)

  override fun execute(ctx: ComputeContext) {
    val model = ctx.model
    val loading = ProductModuleLoading(
      graph = model.pluginGraph,
      contentPlans = ctx.get(Slots.CONTENT_MODULE_PLAN),
      pluginPlans = ctx.get(Slots.PLUGIN_DEPENDENCY_PLAN),
      descriptorLookup = { model.descriptorCache.getOrAnalyze(it.value) },
      pluginLookup = { model.pluginContentCache.getOrExtract(it) },
      testPluginPlans = ctx.get(Slots.TEST_PLUGIN_DEPENDENCY_PLAN),
    )
    for (product in model.discovery.products.sortedBy { it.name }) {
      val testPlugins = model.dslTestPluginsByProduct.get(product.name).orEmpty().filter { it.pluginId == CLION_TEST_PLUGIN }
      val scenarios = if (product.name == "CLion") {
        buildList {
          add(Scenario("Nova", required = true, disabledPluginIds = setOf(CLASSIC_PLUGIN)))
          add(Scenario("Classic", required = false, disabledPluginIds = setOf(RADLER_PLUGIN)))
          if (testPlugins.isNotEmpty()) {
            add(Scenario("Nova tests", required = true, disabledPluginIds = setOf(CLASSIC_PLUGIN), testPlugins = testPlugins))
            add(Scenario("Classic tests", required = false, disabledPluginIds = setOf(RADLER_PLUGIN), testPlugins = testPlugins))
          }
        }
      }
      else {
        buildList {
          add(Scenario(required = product.name == "Rider" || product.name.endsWith("JetBrainsClient")))
          val compatiblePlugins = model.config.nonBundledPlugins.get(product.name).orEmpty()
          if (product.name == "idea" && RADLER_TARGET in compatiblePlugins) {
            add(Scenario("C++ plugin", required = true, additionalPlugins = compatiblePlugins.sortedBy { it.value }))
          }
        }
      }
      for (scenario in scenarios) {
        val result = loading.analyze(
          product.name, product.spec, product.bundledPluginModules + scenario.additionalPlugins, scenario.disabledPluginIds, scenario.testPlugins,
        )
        val unexpected = LinkedHashMap<ContentModuleName, List<String>>()
        if (!scenario.required) {
          unexpected.putAll(result.activationPaths.filterKeys { it.value.startsWith(RD_CLIENT_PREFIX) })
          val compatiblePlugins = model.config.nonBundledPlugins.get(product.name).orEmpty().filterNot { it == RADLER_TARGET }.sortedBy { it.value }
          if (compatiblePlugins.isNotEmpty()) {
            val withCompatiblePlugins = loading.analyze(
              product.name, product.spec, product.bundledPluginModules + compatiblePlugins,
              scenario.disabledPluginIds + RADLER_PLUGIN, scenario.testPlugins,
            )
            for ((module, path) in withCompatiblePlugins.activationPaths) {
              if (module.value.startsWith(RD_CLIENT_PREFIX)) unexpected.putIfAbsent(module, listOf("compatible plugins") + path)
            }
          }
        }
        val missing = if (scenario.required) {
          REQUIRED_MODULES.filterNot { it in result.activationPaths }.associateWith {
            result.exclusions.get(it) ?: "The module is absent from this product."
          }
        }
        else {
          emptyMap()
        }
        if (unexpected.isNotEmpty() || missing.isNotEmpty()) {
          val context = product.name + (scenario.name?.let { " ($it)" } ?: "")
          ctx.emitError(RdClientModuleLoadingError(context, unexpected, missing))
        }
      }
    }
  }
}

private class Scenario(
  val name: String? = null,
  val required: Boolean,
  val disabledPluginIds: Set<PluginId> = emptySet(),
  val testPlugins: List<TestPluginSpec> = emptyList(),
  val additionalPlugins: List<TargetName> = emptyList(),
)

private val CLASSIC_PLUGIN = PluginId("com.intellij.cidr.lang")
private val RADLER_PLUGIN = PluginId("org.jetbrains.plugins.clion.radler")
private val RADLER_TARGET = TargetName("intellij.clion.radler")
private val CLION_TEST_PLUGIN = PluginId("intellij.clion.dev.build.plugin")
private const val RD_CLIENT_PREFIX = "intellij.rd.client"
private val REQUIRED_MODULES = listOf(
  ContentModuleName(RD_CLIENT_PREFIX),
  ContentModuleName("$RD_CLIENT_PREFIX.base"),
)
