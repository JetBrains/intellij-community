// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.productLayout.generator

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.ContentSourceKind
import com.intellij.platform.pluginGraph.PluginId
import com.intellij.platform.pluginGraph.PluginGraph
import org.jetbrains.intellij.build.productLayout.deps.ContentModuleDependencyPlan
import org.jetbrains.intellij.build.productLayout.model.error.ErrorCategory
import org.jetbrains.intellij.build.productLayout.pipeline.ComputeContext
import org.jetbrains.intellij.build.productLayout.pipeline.ContentModuleOutput
import org.jetbrains.intellij.build.productLayout.pipeline.DataSlot
import org.jetbrains.intellij.build.productLayout.pipeline.NodeIds
import org.jetbrains.intellij.build.productLayout.pipeline.PipelineNode
import org.jetbrains.intellij.build.productLayout.pipeline.Slots
import org.jetbrains.intellij.build.productLayout.stats.DependencyFileResult
import org.jetbrains.intellij.build.productLayout.stats.FileChangeStatus
import org.jetbrains.intellij.build.productLayout.util.FileUpdateStrategy
import org.jetbrains.intellij.build.productLayout.xml.buildUpdatedXmlDependenciesContent

/**
 * Writes content module dependency XML files from precomputed plans.
 */
internal object ContentModuleXmlWriter : PipelineNode {
  override val id get() = NodeIds.CONTENT_MODULE_XML_WRITE
  override val requires: Set<DataSlot<*>> get() = setOf(Slots.CONTENT_MODULE_PLAN)
  override val produces: Set<DataSlot<*>> get() = setOf(Slots.CONTENT_MODULE)

  override suspend fun execute(ctx: ComputeContext) {
    val model = ctx.model
    val plans = ctx.get(Slots.CONTENT_MODULE_PLAN).plans
    if (plans.isEmpty()) {
      ctx.publish(Slots.CONTENT_MODULE, ContentModuleOutput(files = emptyList()))
      return
    }

    val results = plans.map { plan ->
      writeContentModuleXml(plan, model.generatedArtifactWritePolicy, model.pluginGraph)
    }

    ctx.publish(Slots.CONTENT_MODULE, ContentModuleOutput(files = results))
  }
}

private fun writeContentModuleXml(
  plan: ContentModuleDependencyPlan,
  policy: FileUpdateStrategy,
  pluginGraph: PluginGraph,
): DependencyFileResult {
  if (plan.suppressibleError?.category == ErrorCategory.NON_STANDARD_DESCRIPTOR_ROOT) {
    return DependencyFileResult(
      contentModuleName = plan.contentModuleName,
      descriptorPath = plan.descriptorPath,
      status = FileChangeStatus.UNCHANGED,
      writtenDependencies = emptyList(),
      testDependencies = emptyList(),
      existingXmlModuleDependencies = emptySet(),
      writtenPluginDependencies = emptyList(),
      allJpsPluginDependencies = emptySet(),
      suppressionUsages = emptyList(),
    )
  }

  val dependencyUpdatedContent = buildUpdatedXmlDependenciesContent(
    content = plan.descriptorContent,
    moduleDependencies = plan.moduleDependencies.map { it.value },
    pluginDependencies = plan.pluginDependencies.map { it.value },
    preserveExistingModule = { moduleName -> plan.suppressedModules.contains(ContentModuleName(moduleName)) },
    preserveExistingPlugin = { pluginName -> plan.suppressedPlugins.contains(PluginId(pluginName)) },
    allowInsideSectionRegion = false,
  ) ?: plan.descriptorContent
  val updatedContent = if (needsQualifiedExtensionPointNames(plan.contentModuleName, pluginGraph)) {
    qualifyModuleSetExtensionPoints(dependencyUpdatedContent)
  }
  else {
    dependencyUpdatedContent
  }
  val status = policy.writeIfChanged(plan.descriptorPath, plan.descriptorContent, updatedContent)

  return DependencyFileResult(
    contentModuleName = plan.contentModuleName,
    descriptorPath = plan.descriptorPath,
    status = status,
    writtenDependencies = plan.moduleDependencies,
    testDependencies = plan.testDependencies,
    existingXmlModuleDependencies = plan.existingXmlModuleDependencies,
    writtenPluginDependencies = plan.writtenPluginDependencies,
    allJpsPluginDependencies = plan.allJpsPluginDependencies,
    suppressionUsages = plan.suppressionUsages,
  )
}

private fun needsQualifiedExtensionPointNames(contentModuleName: ContentModuleName, pluginGraph: PluginGraph): Boolean {
  return pluginGraph.query {
    val contentModule = contentModule(contentModuleName) ?: return@query false
    var hasModuleSetWrapperSource = false
    contentModule.contentProductionSources { source ->
      if (source.kind == ContentSourceKind.PLUGIN && source.plugin().isModuleSetWrapper) {
        hasModuleSetWrapperSource = true
      }
    }
    hasModuleSetWrapperSource
  }
}

private val EXTENSION_POINT_NAME_PATTERN = Regex("""(<extensionPoint\b[^>]*?)\bname="([^"]+)"""")

internal fun qualifyModuleSetExtensionPoints(content: String, namespace: String = "com.intellij"): String {
  return EXTENSION_POINT_NAME_PATTERN.replace(content) { match ->
    val pointName = match.groupValues[2]
    val qualifiedName = if (pointName.startsWith("$namespace.")) pointName else "$namespace.$pointName"
    "${match.groupValues[1]}qualifiedName=\"$qualifiedName\""
  }
}
