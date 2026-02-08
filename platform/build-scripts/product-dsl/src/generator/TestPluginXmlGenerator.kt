// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.productLayout.generator

import org.jetbrains.intellij.build.productLayout.generateTestPluginXml
import org.jetbrains.intellij.build.productLayout.pipeline.ComputeContext
import org.jetbrains.intellij.build.productLayout.pipeline.DataSlot
import org.jetbrains.intellij.build.productLayout.pipeline.NodeIds
import org.jetbrains.intellij.build.productLayout.pipeline.PipelineNode
import org.jetbrains.intellij.build.productLayout.pipeline.Slots
import org.jetbrains.intellij.build.productLayout.pipeline.TestPluginsOutput

/**
 * Generator for test plugin XML files.
 *
 * Generates `plugin.xml` files for test plugins defined in product specs.
 * Test plugins have simpler structure than products - metadata, dependencies, and content modules.
 *
 * **Input:** DSL test plugins from [org.jetbrains.intellij.build.productLayout.pipeline.GenerationModel.dslTestPluginsByProduct]
 * **Output:** Test plugin.xml files in test resources
 *
 * **Publishes:** [Slots.TEST_PLUGINS] with generation results
 *
 * **Requires:** [Slots.TEST_PLUGIN_DEPENDENCY_PLAN]
 *
 * @see org.jetbrains.intellij.build.productLayout.generateTestPluginXml
 */
internal object TestPluginXmlGenerator : PipelineNode {
  override val id get() = NodeIds.TEST_PLUGIN_XML
  override val requires: Set<DataSlot<*>> get() = setOf(Slots.TEST_PLUGIN_DEPENDENCY_PLAN)
  override val produces: Set<DataSlot<*>> get() = setOf(Slots.TEST_PLUGINS)

  override suspend fun execute(ctx: ComputeContext) {
    val model = ctx.model
    val planOutput = ctx.get(Slots.TEST_PLUGIN_DEPENDENCY_PLAN)
    if (planOutput.plans.isEmpty()) {
      ctx.publish(Slots.TEST_PLUGINS, TestPluginsOutput(files = emptyList()))
      return
    }

    val results = planOutput.plans.map { plan ->
      val moduleDependencyChains = model.dslTestPluginDependencyChains.get(plan.spec.pluginId).orEmpty()
      generateTestPluginXml(
        spec = plan.spec,
        productPropertiesClass = plan.productClass,
        projectRoot = model.projectRoot,
        moduleDependencies = plan.moduleDependencies,
        pluginDependencies = plan.pluginDependencies,
        moduleDependencyChains = moduleDependencyChains,
        strategy = model.xmlWritePolicy,
      )
    }
    ctx.publish(Slots.TEST_PLUGINS, TestPluginsOutput(files = results))
  }
}
