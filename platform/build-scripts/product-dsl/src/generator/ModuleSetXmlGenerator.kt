// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.generator

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jetbrains.intellij.build.productLayout.doGenerateAllModuleSetsInternal
import org.jetbrains.intellij.build.productLayout.pipeline.ComputeContext
import org.jetbrains.intellij.build.productLayout.pipeline.DataSlot
import org.jetbrains.intellij.build.productLayout.pipeline.GenerationModel
import org.jetbrains.intellij.build.productLayout.pipeline.ModuleSetsOutput
import org.jetbrains.intellij.build.productLayout.pipeline.NodeIds
import org.jetbrains.intellij.build.productLayout.pipeline.PipelineNode
import org.jetbrains.intellij.build.productLayout.pipeline.Slots
import java.nio.file.Path

/**
 * Generator for module set XML files.
 *
 * Creates `intellij.moduleSets.*.xml` files from Kotlin DSL definitions
 * (e.g., `CommunityModuleSets.kt`, `UltimateModuleSets.kt`).
 *
 * **Input:** Module set sources from [GenerationModel.moduleSetSources]
 * **Output:** XML files in each source's output directory
 *
 * **Publishes:** [Slots.MODULE_SETS] with generation results
 *
 * **No dependencies** - can run immediately (level 0).
 */
internal object ModuleSetXmlGenerator : PipelineNode {
  override val id get() = NodeIds.MODULE_SET_XML
  override val produces: Set<DataSlot<*>> get() = setOf(Slots.MODULE_SETS)

  override suspend fun execute(ctx: ComputeContext) {
    coroutineScope {
      val model = ctx.model
      // Generate module sets for all configured sources in parallel
      val results = model.discovery.moduleSetSources.map { (label, source) ->
        val (sourceObj, outputDir) = source
        async {
          doGenerateAllModuleSetsInternal(
            obj = sourceObj,
            outputDir = outputDir,
            label = label,
            outputProvider = model.outputProvider,
            strategy = model.fileUpdater,
          )
        }
      }.awaitAll()

      // Aggregate tracking maps across all labels
      val aggregatedTrackingMaps = HashMap<Path, MutableSet<String>>()
      for (result in results) {
        for ((dir, files) in result.trackingMap) {
          aggregatedTrackingMaps.computeIfAbsent(dir) { HashSet() }.addAll(files)
        }
      }

      // Build per-label results
      val labelResults = results.map { result ->
        ModuleSetsOutput.LabelResult(
          label = result.label,
          outputDir = result.outputDir,
          files = result.files,
          trackingMap = result.trackingMap,
        )
      }

      ctx.publish(Slots.MODULE_SETS, ModuleSetsOutput(
        resultsByLabel = labelResults,
        trackingMaps = aggregatedTrackingMaps,
      ))
    }
  }
}
