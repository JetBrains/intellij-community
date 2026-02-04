// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.productLayout.generator

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.PluginId
import org.jetbrains.intellij.build.productLayout.deps.PluginDependencyPlan
import org.jetbrains.intellij.build.productLayout.pipeline.ComputeContext
import org.jetbrains.intellij.build.productLayout.pipeline.DataSlot
import org.jetbrains.intellij.build.productLayout.pipeline.NodeIds
import org.jetbrains.intellij.build.productLayout.pipeline.PipelineNode
import org.jetbrains.intellij.build.productLayout.pipeline.PluginXmlOutput
import org.jetbrains.intellij.build.productLayout.pipeline.Slots
import org.jetbrains.intellij.build.productLayout.stats.PluginDependencyFileResult
import org.jetbrains.intellij.build.productLayout.stats.PluginXmlFileResult
import org.jetbrains.intellij.build.productLayout.util.FileUpdateStrategy
import org.jetbrains.intellij.build.productLayout.xml.updateXmlDependencies

/**
 * Writes plugin.xml dependency files from precomputed plans.
 */
internal object PluginXmlWriter : PipelineNode {
  override val id get() = NodeIds.PLUGIN_XML_WRITE
  override val requires: Set<DataSlot<*>> get() = setOf(Slots.PLUGIN_DEPENDENCY_PLAN)
  override val produces: Set<DataSlot<*>> get() = setOf(Slots.PLUGIN_XML)

  override suspend fun execute(ctx: ComputeContext) {
    val model = ctx.model
    val plans = ctx.get(Slots.PLUGIN_DEPENDENCY_PLAN).plans
    if (plans.isEmpty()) {
      ctx.publish(Slots.PLUGIN_XML, PluginXmlOutput(files = emptyList(), detailedResults = emptyList()))
      return
    }

    val files = ArrayList<PluginDependencyFileResult>(plans.size)
    val detailed = ArrayList<PluginXmlFileResult>(plans.size)
    for (plan in plans) {
      val (fileResult, detailedResult) = writePluginXml(plan, model.xmlWritePolicy)
      files.add(fileResult)
      detailed.add(detailedResult)
    }

    ctx.publish(Slots.PLUGIN_XML, PluginXmlOutput(files = files, detailedResults = detailed))
  }
}

private fun writePluginXml(
  plan: PluginDependencyPlan,
  policy: FileUpdateStrategy,
): Pair<PluginDependencyFileResult, PluginXmlFileResult> {
  val status = updateXmlDependencies(
    path = plan.pluginXmlPath,
    content = plan.pluginXmlContent,
    moduleDependencies = plan.moduleDependencies.map { it.value },
    pluginDependencies = plan.pluginDependencies.map { it.value },
    preserveExistingModule = { moduleName -> plan.preserveExistingModuleDependencies.contains(ContentModuleName(moduleName)) },
    preserveExistingPlugin = { pluginName -> plan.preserveExistingPluginDependencies.contains(PluginId(pluginName)) },
    legacyPluginDependencies = plan.legacyPluginDependencies.map { it.value },
    xiIncludeModuleDeps = plan.xiIncludeModuleDeps,
    xiIncludePluginDeps = plan.xiIncludePluginDeps,
    strategy = policy,
  )

  val dependencyCount = plan.moduleDependencies.size + plan.pluginDependencies.size

  val fileResult = PluginDependencyFileResult(
    pluginContentModuleName = plan.pluginContentModuleName,
    pluginXmlPath = plan.pluginXmlPath,
    status = status,
    dependencyCount = dependencyCount,
  )
  val detailedResult = PluginXmlFileResult(
    pluginContentModuleName = plan.pluginContentModuleName,
    pluginXmlPath = plan.pluginXmlPath,
    status = status,
    dependencyCount = dependencyCount,
    suppressionUsages = plan.suppressionUsages,
  )
  return fileResult to detailedResult
}
