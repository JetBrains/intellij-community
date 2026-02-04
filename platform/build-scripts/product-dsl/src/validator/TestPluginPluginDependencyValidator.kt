// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.productLayout.validator

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.PluginId
import com.intellij.platform.plugins.parser.impl.parseContentAndXIncludes
import org.jetbrains.intellij.build.productLayout.LIB_MODULE_PREFIX
import org.jetbrains.intellij.build.productLayout.TestPluginSpec
import org.jetbrains.intellij.build.productLayout.buildContentBlocksAndChainMapping
import org.jetbrains.intellij.build.productLayout.discovery.extractLegacyDepends
import org.jetbrains.intellij.build.productLayout.model.error.MissingTestPluginPluginDependencyError
import org.jetbrains.intellij.build.productLayout.model.error.ValidationError
import org.jetbrains.intellij.build.productLayout.pipeline.ComputeContext
import org.jetbrains.intellij.build.productLayout.pipeline.DataSlot
import org.jetbrains.intellij.build.productLayout.pipeline.NodeIds
import org.jetbrains.intellij.build.productLayout.pipeline.PipelineNode
import org.jetbrains.intellij.build.productLayout.pipeline.Slots
import org.jetbrains.intellij.build.productLayout.traversal.OwningPlugin
import org.jetbrains.intellij.build.productLayout.traversal.collectBundledPluginNames
import org.jetbrains.intellij.build.productLayout.traversal.collectOwningPlugins
import java.nio.file.Files
import java.nio.file.Path

/**
 * Test plugin dependency validation.
 *
 * Purpose: Ensure DSL test plugins declare plugin dependencies required by their content module deps.
 * Inputs: `Slots.CONTENT_MODULE`, `Slots.TEST_PLUGINS`, plugin graph, DSL test plugin specs.
 * Output: `MissingTestPluginPluginDependencyError`.
 * Auto-fix: none.
 *
 * Glossary: docs/validators/README.md.
 * Spec: docs/validators/test-plugin-plugin-dependency.md.
 */
internal object TestPluginPluginDependencyValidator : PipelineNode {
  override val id get() = NodeIds.TEST_PLUGIN_PLUGIN_DEPENDENCY_VALIDATION

  override val requires: Set<DataSlot<*>> get() = setOf(Slots.CONTENT_MODULE, Slots.TEST_PLUGINS)

  override suspend fun execute(ctx: ComputeContext) {
    val model = ctx.model
    val testPluginsByProduct = model.dslTestPluginsByProduct
    if (testPluginsByProduct.isEmpty()) return

    val contentModuleResults = ctx.get(Slots.CONTENT_MODULE).files
    if (contentModuleResults.isEmpty()) return

    val depsByModule = contentModuleResults.associateBy { it.contentModuleName }
    val owningPluginsCache = HashMap<ContentModuleName, Set<OwningPlugin>>()
    val expectedContentByPath = HashMap<Path, String>()
    for (diff in model.fileUpdater.getDiffs()) {
      expectedContentByPath[diff.path] = diff.expectedContent
    }

    val errors = ArrayList<ValidationError>()
    for ((productName, specs) in testPluginsByProduct) {
      if (specs.isEmpty()) continue

      val bundledPluginNames = collectBundledPluginNames(model.pluginGraph, productName)
      for (spec in specs) {
        val contentData = buildContentBlocksAndChainMapping(spec.spec, collectModuleSetAliases = false)
        val contentModules = contentData.contentBlocks
          .flatMap { it.modules }
          .mapTo(LinkedHashSet()) { it.name }
        if (contentModules.isEmpty()) continue

        val declaredPluginDeps = readDeclaredTestPluginDependencies(spec, model.projectRoot, expectedContentByPath)
        val allowedMissingByModule = buildAllowedMissingByModule(contentData)
        val dependencyChains = model.dslTestPluginDependencyChains.get(spec.pluginId) ?: emptyMap()
        val globalAllowedMissing = spec.allowedMissingPluginIds.toSet()
        val resolvableOwners = if (spec.additionalBundledPluginTargetNames.isEmpty()) {
          bundledPluginNames
        }
        else {
          bundledPluginNames + spec.additionalBundledPluginTargetNames
        }

        val missingByPlugin = LinkedHashMap<PluginId, LinkedHashSet<ContentModuleName>>()
        for (moduleName in contentModules) {
          val moduleDeps = depsByModule.get(moduleName)?.testDependencies ?: continue
          if (moduleDeps.isEmpty()) continue

          val moduleAllowedMissing = effectiveAllowedMissingPluginIds(
            moduleName = moduleName,
            allowedMissingByModule = allowedMissingByModule,
            dependencyChains = dependencyChains,
            globalAllowedMissing = globalAllowedMissing,
          )

          for (dependency in moduleDeps) {
            if (dependency in contentModules) continue
            if (dependency.value.startsWith(LIB_MODULE_PREFIX)) continue

            val owningPlugins = owningPluginsCache.getOrPut(dependency) {
              collectOwningPlugins(model.pluginGraph, dependency, includeTestSources = true)
            }
            val owningProdPlugins = owningPlugins.filterNot { it.isTest }
            if (owningProdPlugins.isEmpty()) continue

            val resolvableProdOwners = owningProdPlugins.filter { it.name in resolvableOwners }
            if (resolvableProdOwners.isEmpty()) continue

            for (owner in resolvableProdOwners) {
              val pluginId = owner.pluginId
              if (pluginId == spec.pluginId) continue
              if (pluginId in moduleAllowedMissing) continue
              if (pluginId !in declaredPluginDeps) {
                missingByPlugin.computeIfAbsent(pluginId) { LinkedHashSet() }.add(moduleName)
              }
            }
          }
        }

        if (missingByPlugin.isNotEmpty()) {
          errors.add(
            MissingTestPluginPluginDependencyError(
              context = spec.pluginId.value,
              testPluginId = spec.pluginId,
              productName = productName,
              missingPluginIds = missingByPlugin.keys,
              requiredByModules = missingByPlugin,
            )
          )
        }
      }
    }

    if (errors.isNotEmpty()) {
      ctx.emitErrors(errors)
    }
  }
}

private fun readDeclaredTestPluginDependencies(
  spec: TestPluginSpec,
  projectRoot: Path,
  expectedContentByPath: Map<Path, String>,
): Set<PluginId> {
  val pluginXmlPath = projectRoot.resolve(spec.pluginXmlPath)
  val content = expectedContentByPath.get(pluginXmlPath)
                ?: if (Files.exists(pluginXmlPath)) Files.readString(pluginXmlPath) else return emptySet()
  if (content.isBlank()) return emptySet()

  val parseResult = parseContentAndXIncludes(input = content.toByteArray(), locationSource = null)
  val pluginDeps = LinkedHashSet<PluginId>()
  for (dep in parseResult.pluginDependencies) {
    pluginDeps.add(PluginId(dep))
  }
  for (legacy in extractLegacyDepends(content)) {
    pluginDeps.add(legacy.pluginId)
  }
  return pluginDeps
}

private fun buildAllowedMissingByModule(
  contentData: org.jetbrains.intellij.build.productLayout.ContentBuildData,
): Map<ContentModuleName, Set<PluginId>> {
  val result = HashMap<ContentModuleName, Set<PluginId>>()
  for (block in contentData.contentBlocks) {
    for (module in block.modules) {
      val allowed = module.allowedMissingPluginIds
      if (allowed.isNotEmpty()) {
        result[module.name] = allowed.toSet()
      }
    }
  }
  return result
}

private fun effectiveAllowedMissingPluginIds(
  moduleName: ContentModuleName,
  allowedMissingByModule: Map<ContentModuleName, Set<PluginId>>,
  dependencyChains: Map<ContentModuleName, List<ContentModuleName>>,
  globalAllowedMissing: Set<PluginId>,
): Set<PluginId> {
  val moduleAllowed = allowedMissingByModule.get(moduleName) ?: emptySet()
  val rootModule = dependencyChains.get(moduleName)?.firstOrNull()
  val rootAllowed = if (rootModule == null) emptySet() else allowedMissingByModule.get(rootModule) ?: emptySet()

  if (moduleAllowed.isEmpty() && rootAllowed.isEmpty() && globalAllowedMissing.isEmpty()) {
    return emptySet()
  }

  val result = LinkedHashSet<PluginId>(moduleAllowed.size + rootAllowed.size + globalAllowedMissing.size)
  result.addAll(moduleAllowed)
  result.addAll(rootAllowed)
  result.addAll(globalAllowedMissing)
  return result
}
