// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.productLayout.validator

import com.intellij.platform.pluginGraph.PluginGraph
import com.intellij.platform.pluginGraph.PluginModuleId
import com.intellij.platform.pluginGraph.PluginNode
import com.intellij.platform.pluginGraph.ProductNode
import com.intellij.platform.pluginGraph.toActualId
import org.jetbrains.intellij.build.productLayout.model.error.DuplicatePluginContentModulesError
import org.jetbrains.intellij.build.productLayout.model.error.ValidationError
import org.jetbrains.intellij.build.productLayout.pipeline.ComputeContext
import org.jetbrains.intellij.build.productLayout.pipeline.DataSlot
import org.jetbrains.intellij.build.productLayout.pipeline.NodeIds
import org.jetbrains.intellij.build.productLayout.pipeline.PipelineNode

/**
 * Duplicate plugin content module validation.
 *
 * Purpose: Detect one runtime content module ID that two bundled plugins of one product declare.
 * The subject is the runtime ID, which `PluginModuleId.toActualId` builds from the module name and the namespace.
 * The runtime keeps one descriptor per ID. `resolveIdConflicts` drops a whole plugin to break the tie.
 * So a shared ID makes a bundled plugin disappear.
 *
 * The rule reports a production plugin paired with a production plugin, and a production plugin paired with a test
 * plugin. A pair of test plugins stays out, because a product loads one test plugin at a time.
 *
 * A content module that two plugins declare in a `<content>` tag with no namespace stays legal.
 * `toActualId` gives each such copy an implicit namespace per plugin, so the runtime IDs differ and no pair forms.
 * Read `docs/IntelliJ-Platform/4_man/Plugin-Model/Including-content-module-in-multiple-plugins.md`, which is
 * IJPL-A-1893. `ContentModuleCopyConflictValidator` holds the classloader half of that shape.
 *
 * Inputs: plugin graph product bundling and plugin content edges.
 * Output: `DuplicatePluginContentModulesError`.
 * Auto-fix: none.
 *
 * Glossary: docs/validators/README.md.
 * Spec: docs/validators/plugin-content-duplicates.md.
 */
internal object PluginContentDuplicatesValidator : PipelineNode {
  override val id get() = NodeIds.PLUGIN_CONTENT_DUPLICATE_VALIDATION
  override val requires: Set<DataSlot<*>> get() = emptySet()

  override suspend fun execute(ctx: ComputeContext) {
    val model = ctx.model
    ctx.emitErrorsPerProduct(model.pluginGraph) { product ->
      validateDuplicatePluginContentModulesForProduct(product, model.pluginGraph)
    }
  }
}

private fun validateDuplicatePluginContentModulesForProduct(
  productV: ProductNode,
  pluginGraph: PluginGraph,
): List<ValidationError> = pluginGraph.query {
  val productName = productV.name()
  val productionOwners = LinkedHashMap<PluginModuleId, LinkedHashSet<DuplicatePluginContentModulesError.PluginOwner>>()
  val testOwners = LinkedHashMap<PluginModuleId, LinkedHashSet<DuplicatePluginContentModulesError.PluginOwner>>()

  fun recordPluginModules(
    plugin: PluginNode,
    isTest: Boolean,
    target: MutableMap<PluginModuleId, LinkedHashSet<DuplicatePluginContentModulesError.PluginOwner>>,
  ) {
    val owner = DuplicatePluginContentModulesError.PluginOwner(plugin.name(), isTest)
    plugin.containsContentWithNamespace { module, _ -> target.getOrPut(module.moduleId().toActualId(plugin.pluginId)) { LinkedHashSet() }.add(owner) }
    plugin.containsContentWithNamespaceTest { module, _ -> target.getOrPut(module.moduleId().toActualId(plugin.pluginId)) { LinkedHashSet() }.add(owner) }
  }

  productV.bundles { plugin -> recordPluginModules(plugin, isTest = false, target = productionOwners) }
  productV.bundlesTest { plugin -> recordPluginModules(plugin, isTest = true, target = testOwners) }

  val duplicates = LinkedHashMap<PluginModuleId, List<DuplicatePluginContentModulesError.PluginOwner>>()
  for ((moduleId, prodOwners) in productionOwners) {
    val testOwnersForModule = testOwners[moduleId] ?: emptySet()
    // A test plugin alone never forms a pair, so one production owner needs a second owner from either side.
    if (prodOwners.size == 1 && testOwnersForModule.isEmpty()) {
      continue
    }

    val combined = LinkedHashSet<DuplicatePluginContentModulesError.PluginOwner>(
      prodOwners.size + testOwnersForModule.size
    )
    combined.addAll(prodOwners)
    combined.addAll(testOwnersForModule)
    duplicates[moduleId] = combined.sortedBy { it.pluginName.value }
  }

  if (duplicates.isEmpty()) {
    return emptyList()
  }

  return listOf(DuplicatePluginContentModulesError(context = productName, duplicates = duplicates))
}
