// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package org.jetbrains.intellij.build.productLayout.generator

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.DependencyClassification
import com.intellij.platform.pluginGraph.PluginGraph
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jetbrains.intellij.build.productLayout.LIB_MODULE_PREFIX
import org.jetbrains.intellij.build.productLayout.ModuleSet
import org.jetbrains.intellij.build.productLayout.pipeline.ComputeContext
import org.jetbrains.intellij.build.productLayout.pipeline.DataSlot
import org.jetbrains.intellij.build.productLayout.pipeline.GenerationModel
import org.jetbrains.intellij.build.productLayout.pipeline.NodeIds
import org.jetbrains.intellij.build.productLayout.pipeline.PipelineNode
import org.jetbrains.intellij.build.productLayout.pipeline.ProductModuleDepsOutput
import org.jetbrains.intellij.build.productLayout.pipeline.Slots
import org.jetbrains.intellij.build.productLayout.stats.DependencyFileResult
import org.jetbrains.intellij.build.productLayout.stats.SuppressionType
import org.jetbrains.intellij.build.productLayout.stats.SuppressionUsage
import org.jetbrains.intellij.build.productLayout.xml.updateXmlDependencies
import org.jetbrains.intellij.build.productLayout.xml.visitAllModules

/**
 * Generator for product module dependency XML files.
 *
 * Generates `<dependencies>` sections in module descriptor XML files for modules
 * declared in module sets (e.g., `corePlatform()`, `ideCommon()`).
 *
 * **Input:** Module sets from [GenerationModel.discovery]
 * **Output:** Updated `moduleName.xml` descriptor files with dependencies
 *
 * **Publishes:** [Slots.PRODUCT_MODULE_DEPS] with generation results
 *
 * **No dependencies** - can run immediately (level 0).
 *
 * @see org.jetbrains.intellij.build.productLayout.validator.SelfContainedModuleSetValidator for self-contained module set validation
 * @see org.jetbrains.intellij.build.productLayout.validator.ProductModuleSetValidator for product module set validation
 * @see org.jetbrains.intellij.build.productLayout.validator.LibraryModuleValidator for library module dependency validation
 */
internal object ProductModuleDependencyGenerator : PipelineNode {
  override val id get() = NodeIds.PRODUCT_MODULE_DEPS
  override val produces: Set<DataSlot<*>> get() = setOf(Slots.PRODUCT_MODULE_DEPS)

  override suspend fun execute(ctx: ComputeContext) {
    coroutineScope {
      val model = ctx.model
      val allModuleSets = model.discovery.communityModuleSets + model.discovery.coreModuleSets + model.discovery.ultimateModuleSets
      val modulesToProcess = collectModulesToProcess(allModuleSets)
      if (modulesToProcess.isEmpty()) {
        ctx.publish(Slots.PRODUCT_MODULE_DEPS, ProductModuleDepsOutput(files = emptyList()))
        return@coroutineScope
      }

      val cache = model.descriptorCache
      val graph = model.pluginGraph
      val strategy = model.xmlWritePolicy
      val suppressionConfig = model.suppressionConfig
      val updateSuppressions = model.updateSuppressions

      // Write XML files in parallel
      val results = modulesToProcess.map { moduleName ->
        async {
          val info = cache.getOrAnalyze(moduleName) ?: return@async null
          if (info.skipDependencyGeneration) {
            return@async null
          }

          val contentModuleName = ContentModuleName(moduleName)
          val suppressedModules = suppressionConfig.getSuppressedModules(contentModuleName)

          // Compute dependencies from graph (only content modules - those with descriptors)
          val dependencies = computeProductModuleDeps(graph, moduleName, model.config.libraryModuleFilter).map(::ContentModuleName)
          val dependencyNames = dependencies.mapTo(HashSet()) { it.value }
          val existingXmlModules = info.existingModuleDependencies.toSet()
          val existingXmlModulesAsContentModuleName = existingXmlModules.mapTo(HashSet(), ::ContentModuleName)
          val effectiveSuppressedModules = computeEffectiveSuppressedDeps(
            updateSuppressions = updateSuppressions,
            existingXmlDeps = existingXmlModulesAsContentModuleName,
            jpsDeps = dependencies.toSet(),
            suppressedDeps = suppressedModules,
          )
          val suppressionUsages = ArrayList<SuppressionUsage>()
          val moduleDeps = collectModuleDepsWithSuppressions(
            contentModuleName = contentModuleName,
            dependencies = dependencies,
            suppressedModules = effectiveSuppressedModules,
            suppressionUsages = suppressionUsages,
          )

          for (existingDep in existingXmlModules) {
            val notInGraph = existingDep !in dependencyNames
            if (notInGraph && effectiveSuppressedModules.contains(ContentModuleName(existingDep))) {
              suppressionUsages.add(SuppressionUsage(contentModuleName, existingDep, SuppressionType.MODULE_DEP))
            }
          }

          val status = updateXmlDependencies(
            path = info.descriptorPath,
            content = info.content,
            moduleDependencies = moduleDeps.distinct().sorted(),
            preserveExistingModule = { moduleNameToPreserve ->
              effectiveSuppressedModules.contains(ContentModuleName(moduleNameToPreserve))
            },
            preserveExistingPlugin = { true },
            allowInsideSectionRegion = false,
            strategy = strategy,
          )
          DependencyFileResult(
            contentModuleName = contentModuleName,
            descriptorPath = info.descriptorPath,
            status = status,
            writtenDependencies = moduleDeps.sorted().map(::ContentModuleName),
            existingXmlModuleDependencies = existingXmlModulesAsContentModuleName,
            suppressionUsages = suppressionUsages,
          )
        }
      }.awaitAll().filterNotNull()

      ctx.publish(Slots.PRODUCT_MODULE_DEPS, ProductModuleDepsOutput(files = results))
    }
  }
}

/**
 * Computes JPS dependencies for a product module that are content modules (have descriptors).
 *
 * Uses graph's EDGE_TARGET_DEPENDS_ON edges and filters to only those that classify as ModuleDep
 * (i.e., have a content module descriptor).
 *
 * Only includes COMPILE and RUNTIME scope deps (excludes TEST and PROVIDED).
 */
private fun computeProductModuleDeps(
  graph: PluginGraph,
  moduleName: String,
  libraryModuleFilter: (String) -> Boolean,
): List<String> {
  val deps = ArrayList<String>()
  graph.query {
    val target = target(moduleName) ?: return@query
    target.dependsOn { dep ->
      // Only include COMPILE and RUNTIME scope (production deps)
      if (!dep.isProduction()) return@dependsOn
      // Only include deps that are content modules (have descriptors)
      when (val c = classifyTarget(dep.targetId)) {
        is DependencyClassification.ModuleDep -> {
          val depName = c.moduleName.value
          if (depName == moduleName) {
            return@dependsOn
          }
          if (depName.startsWith(LIB_MODULE_PREFIX) && !libraryModuleFilter(depName)) {
            return@dependsOn
          }
          deps.add(depName)
        }
        else -> {}
      }
    }
  }
  return deps.distinct().sorted()
}

private fun collectModulesToProcess(moduleSets: List<ModuleSet>): Set<String> {
  val result = LinkedHashSet<String>()
  for (set in moduleSets) {
    visitAllModules(set) { module ->
      if (module.includeDependencies ||
          module.name.value.startsWith(LIB_MODULE_PREFIX) ||
          module.name.value.startsWith("intellij.platform.settings.")) {
        result.add(module.name.value)
      }
    }
  }
  return result
}
