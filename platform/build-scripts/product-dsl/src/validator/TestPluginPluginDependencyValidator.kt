// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.productLayout.validator

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.PluginId
import org.jetbrains.intellij.build.productLayout.TestPluginSpec
import org.jetbrains.intellij.build.productLayout.buildContentBlocksAndChainMapping
import org.jetbrains.intellij.build.productLayout.deps.TestPluginUnresolvedDependency
import org.jetbrains.intellij.build.productLayout.deps.buildAllowedMissingByModule
import org.jetbrains.intellij.build.productLayout.deps.parseTestPluginXmlDependencies
import org.jetbrains.intellij.build.productLayout.deps.resolveAllowedMissingPluginIds
import org.jetbrains.intellij.build.productLayout.model.error.DslTestPluginDependencyError
import org.jetbrains.intellij.build.productLayout.model.error.DslTestPluginDependencyKind
import org.jetbrains.intellij.build.productLayout.model.error.FileDiff
import org.jetbrains.intellij.build.productLayout.model.error.MissingTestPluginPluginDependencyError
import org.jetbrains.intellij.build.productLayout.model.error.ValidationError
import org.jetbrains.intellij.build.productLayout.pipeline.ComputeContext
import org.jetbrains.intellij.build.productLayout.pipeline.DataSlot
import org.jetbrains.intellij.build.productLayout.pipeline.NodeIds
import org.jetbrains.intellij.build.productLayout.pipeline.PipelineNode
import org.jetbrains.intellij.build.productLayout.pipeline.Slots
import java.nio.file.Files
import java.nio.file.Path

/**
 * Test plugin dependency validation.
 *
 * Purpose: Ensure DSL test plugins declare plugin dependencies required by their content module deps.
 * Inputs: `Slots.TEST_PLUGIN_DEPENDENCY_PLAN`, `Slots.TEST_PLUGINS`.
 * Output: `MissingTestPluginPluginDependencyError`.
 * Auto-fix: none.
 *
 * Glossary: docs/validators/README.md.
 * Spec: docs/validators/test-plugin-plugin-dependency.md.
 */
internal object TestPluginPluginDependencyValidator : PipelineNode {
  override val id get() = NodeIds.TEST_PLUGIN_PLUGIN_DEPENDENCY_VALIDATION

  override val requires: Set<DataSlot<*>> get() = setOf(
    Slots.TEST_PLUGIN_DEPENDENCY_PLAN,
    Slots.TEST_PLUGINS,
  )

  override suspend fun execute(ctx: ComputeContext) {
    val model = ctx.model
    val testPluginsByProduct = model.dslTestPluginsByProduct
    if (testPluginsByProduct.isEmpty()) return

    val planOutput = ctx.get(Slots.TEST_PLUGIN_DEPENDENCY_PLAN)
    if (planOutput.plans.isEmpty()) return

    val plansByPluginId = planOutput.plansByPluginId

    val errors = ArrayList<ValidationError>()
    for ((productName, specs) in testPluginsByProduct) {
      if (specs.isEmpty()) continue
      for (spec in specs) {
        val plan = plansByPluginId[spec.pluginId] ?: continue
        val declaredPluginDeps = readDeclaredPluginDependencies(model.fileUpdater.getDiffs(), model.projectRoot, spec)
        val contentData = buildContentBlocksAndChainMapping(spec.spec, collectModuleSetAliases = false)
        val allowedMissingByModule = buildAllowedMissingByModule(contentData)
        val dependencyChains = model.dslTestPluginDependencyChains.get(spec.pluginId) ?: emptyMap()
        val globalAllowedMissing = spec.allowedMissingPluginIds.toSet()

        val missingByPlugin = LinkedHashMap<PluginId, LinkedHashSet<ContentModuleName>>()
        for ((pluginId, modules) in plan.requiredByPlugin) {
          if (pluginId in declaredPluginDeps) continue
          val missingModules = LinkedHashSet<ContentModuleName>()
          for (moduleName in modules) {
            val moduleAllowedMissing = resolveAllowedMissingPluginIds(
              moduleName = moduleName,
              allowedMissingByModule = allowedMissingByModule,
              dependencyChains = dependencyChains,
              globalAllowedMissing = globalAllowedMissing,
            )
            if (pluginId !in moduleAllowedMissing) {
              missingModules.add(moduleName)
            }
          }
          if (missingModules.isNotEmpty()) {
            missingByPlugin[pluginId] = missingModules
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

        errors.addAll(
          buildUnresolvedDependencyErrors(
            plan = plan,
            spec = spec,
            productName = productName,
            allowedMissingByModule = allowedMissingByModule,
            dependencyChains = dependencyChains,
            globalAllowedMissing = globalAllowedMissing,
          )
        )
      }
    }

    if (errors.isNotEmpty()) {
      ctx.emitErrors(errors)
    }
  }
}

private fun buildUnresolvedDependencyErrors(
  plan: org.jetbrains.intellij.build.productLayout.pipeline.TestPluginDependencyPlan,
  spec: TestPluginSpec,
  productName: String,
  allowedMissingByModule: Map<ContentModuleName, Set<PluginId>>,
  dependencyChains: Map<ContentModuleName, List<ContentModuleName>>,
  globalAllowedMissing: Set<PluginId>,
): List<ValidationError> {
  if (plan.unresolvedDependencies.isEmpty()) return emptyList()

  val errors = ArrayList<ValidationError>()
  for (dependency in plan.unresolvedDependencies) {
    when (dependency) {
      is TestPluginUnresolvedDependency.ContentModule -> {
        val moduleAllowedMissing = resolveAllowedMissingPluginIds(
          moduleName = dependency.dependencyId,
          allowedMissingByModule = allowedMissingByModule,
          dependencyChains = dependencyChains,
          globalAllowedMissing = globalAllowedMissing,
        )
        val disallowedOwners = dependency.owningPlugins.filterNot { it.pluginId in moduleAllowedMissing }
        if (disallowedOwners.isEmpty()) continue
        errors.add(
          DslTestPluginDependencyError(
            context = spec.pluginId.value,
            testPluginId = spec.pluginId,
            productName = productName,
            dependencyKind = DslTestPluginDependencyKind.CONTENT_MODULE,
            contentModuleDependencyId = dependency.dependencyId,
            owningPlugins = disallowedOwners.toSet(),
            dependencySource = dependency.dependencySource,
          )
        )
      }
      is TestPluginUnresolvedDependency.Plugin -> {
        if (dependency.dependencyId in globalAllowedMissing) continue
        errors.add(
          DslTestPluginDependencyError(
            context = spec.pluginId.value,
            testPluginId = spec.pluginId,
            productName = productName,
            dependencyKind = DslTestPluginDependencyKind.PLUGIN,
            pluginDependencyId = dependency.dependencyId,
            dependencyTargetNames = dependency.dependencyTargetNames,
          )
        )
      }
    }
  }
  return errors
}

private fun readDeclaredPluginDependencies(
  diffs: List<FileDiff>,
  projectRoot: Path,
  spec: TestPluginSpec,
): Set<PluginId> {
  val pluginXmlPath = projectRoot.resolve(spec.pluginXmlPath)
  val content = readPluginXmlContent(diffs, pluginXmlPath)
  return parseTestPluginXmlDependencies(content).pluginDependencies
}

private fun readPluginXmlContent(diffs: List<FileDiff>, path: Path): String {
  val diff = diffs.firstOrNull { it.path == path }
  if (diff != null) {
    return diff.expectedContent
  }
  return if (Files.exists(path)) Files.readString(path) else ""
}
