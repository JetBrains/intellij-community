// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.validator

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.ContentSourceKind
import com.intellij.platform.pluginGraph.PluginGraph
import com.intellij.platform.pluginGraph.TargetName
import com.intellij.platform.pluginGraph.baseModuleName
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.productLayout.model.error.ContentModuleBackingIssue
import org.jetbrains.intellij.build.productLayout.model.error.ContentModuleBackingIssueKind
import org.jetbrains.intellij.build.productLayout.model.error.MissingContentModuleBackingError
import org.jetbrains.intellij.build.productLayout.model.error.ValidationError
import org.jetbrains.intellij.build.productLayout.pipeline.ComputeContext
import org.jetbrains.intellij.build.productLayout.pipeline.DataSlot
import org.jetbrains.intellij.build.productLayout.pipeline.NodeIds
import org.jetbrains.intellij.build.productLayout.pipeline.PipelineNode

/**
 * Validates that all declared content modules are backed by real JPS modules.
 *
 * Purpose: Fail fast on typos or stale module names across module sets, products, and plugins.
 * Inputs: plugin graph + JPS model via [ModuleOutputProvider].
 * Output: [MissingContentModuleBackingError].
 */
internal object ContentModuleBackingValidator : PipelineNode {
  override val id get() = NodeIds.CONTENT_MODULE_BACKING_VALIDATION

  override val requires: Set<DataSlot<*>>
    get() = emptySet()

  override suspend fun execute(ctx: ComputeContext) {
    val model = ctx.model
    val errors = validateContentModuleBacking(
      pluginGraph = model.pluginGraph,
      outputProvider = model.outputProvider,
    )
    ctx.emitErrors(errors)
  }
}

private data class ContentModuleSnapshot(
  val name: ContentModuleName,
  val backingTargets: List<TargetName>,
  val sources: Set<String>,
)

private fun validateContentModuleBacking(
  pluginGraph: PluginGraph,
  outputProvider: ModuleOutputProvider,
): List<ValidationError> {
  val snapshots = pluginGraph.query {
    val result = ArrayList<ContentModuleSnapshot>()
    contentModules { module ->
      val sources = LinkedHashSet<String>()
      module.contentSources { source ->
        when (source.kind) {
          ContentSourceKind.PLUGIN -> {
            val testSuffix = if (source.plugin().isTest) " (test)" else ""
            sources.add("plugin: ${source.name()}$testSuffix")
          }
          ContentSourceKind.PRODUCT -> sources.add("product: ${source.name()}")
          ContentSourceKind.MODULE_SET -> sources.add("module set: ${source.name()}")
        }
      }

      if (sources.isEmpty()) {
        return@contentModules
      }

      val backingTargets = ArrayList<TargetName>()
      module.backedBy { target ->
        backingTargets.add(TargetName(target.name()))
      }

      result.add(ContentModuleSnapshot(
        name = module.name(),
        backingTargets = backingTargets,
        sources = sources,
      ))
    }
    result
  }

  if (snapshots.isEmpty()) {
    return emptyList()
  }

  val issues = LinkedHashMap<ContentModuleName, ContentModuleBackingIssue>()
  for (snapshot in snapshots) {
    val moduleName = snapshot.name
    val expectedTarget = moduleName.baseModuleName().value
    val backingTargets = snapshot.backingTargets
    val issue = when {
      backingTargets.isEmpty() -> ContentModuleBackingIssue(
        reason = ContentModuleBackingIssueKind.NO_BACKING_TARGET,
        expectedTarget = expectedTarget,
        sources = snapshot.sources,
      )
      backingTargets.size > 1 -> ContentModuleBackingIssue(
        reason = ContentModuleBackingIssueKind.MULTIPLE_BACKING_TARGETS,
        backingTargets = backingTargets.mapTo(LinkedHashSet()) { it.value },
        expectedTarget = expectedTarget,
        sources = snapshot.sources,
      )
      backingTargets.single().value != expectedTarget -> ContentModuleBackingIssue(
        reason = ContentModuleBackingIssueKind.MISMATCHED_BACKING_TARGET,
        backingTargets = setOf(backingTargets.single().value),
        expectedTarget = expectedTarget,
        sources = snapshot.sources,
      )
      outputProvider.findModule(expectedTarget) == null -> ContentModuleBackingIssue(
        reason = ContentModuleBackingIssueKind.MISSING_JPS_MODULE,
        backingTargets = setOf(expectedTarget),
        expectedTarget = expectedTarget,
        sources = snapshot.sources,
      )
      else -> null
    }

    if (issue != null) {
      issues[moduleName] = issue
    }
  }

  if (issues.isEmpty()) {
    return emptyList()
  }

  return listOf(
    MissingContentModuleBackingError(
      context = "content-modules",
      missingModules = issues,
    )
  )
}
