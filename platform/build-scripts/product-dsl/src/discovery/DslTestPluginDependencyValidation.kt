// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.build.productLayout.discovery

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.TargetName
import org.jetbrains.intellij.build.productLayout.TestPluginSpec
import org.jetbrains.intellij.build.productLayout.debug
import org.jetbrains.intellij.build.productLayout.model.ErrorSink
import org.jetbrains.intellij.build.productLayout.model.error.DslTestPluginDependencyError
import org.jetbrains.intellij.build.productLayout.model.error.DslTestPluginDependencyKind
import org.jetbrains.intellij.build.productLayout.model.error.DslTestPluginDependencySource
import org.jetbrains.intellij.build.productLayout.model.error.DslTestPluginOwner
import org.jetbrains.intellij.build.productLayout.stats.SuppressionType
import org.jetbrains.intellij.build.productLayout.stats.SuppressionUsage
import org.jetbrains.intellij.build.productLayout.traversal.OwningPlugin

/**
 * DSL test plugin dependency validation runs during DSL expansion (model building), not in a pipeline node,
 * because it relies on raw JPS dependency traversal and suppression decisions that are not represented
 * in the filtered plugin graph used by validators.
 */
internal fun validateDslTestPluginOwnedDependency(
  depName: ContentModuleName,
  moduleName: ContentModuleName,
  scopeName: String?,
  isDeclaredInSpec: Boolean,
  declaredRootModule: ContentModuleName?,
  testPluginSpec: TestPluginSpec,
  productName: String,
  bundledPluginNames: Set<TargetName>,
  allowedMissingPluginIds: Set<String>,
  owningProdPlugins: List<OwningPlugin>,
  updateSuppressions: Boolean,
  suppressionUsageSink: MutableList<SuppressionUsage>?,
  errorSink: ErrorSink,
) {
  val resolvableOwners = owningProdPlugins.filter {
    it.name in bundledPluginNames || it.name in testPluginSpec.additionalBundledPluginTargetNames
  }
  if (resolvableOwners.isNotEmpty()) {
    debug("dslTestDeps") {
      "skip plugin-owned dep=$depName from=$moduleName owners=${owningProdPlugins.joinToString { it.pluginId.value }}"
    }
    return
  }

  val unresolvedOwners = owningProdPlugins.filter { it.pluginId.value != testPluginSpec.pluginId.value }
  val disallowedOwners = unresolvedOwners.filterNot { it.pluginId.value in allowedMissingPluginIds }
  if (disallowedOwners.isEmpty()) {
    return
  }


  if (updateSuppressions) {
    val suppressionSource = declaredRootModule ?: moduleName
    suppressionUsageSink?.add(SuppressionUsage(suppressionSource, depName.value, SuppressionType.MODULE_DEP))
    debug("dslTestDeps") { "suppress unresolved plugin-owned dep=$depName from=$moduleName" }
    return
  }

  val dependencySource = DslTestPluginDependencySource(
    fromModule = moduleName,
    scope = scopeName,
    isDeclaredInSpec = isDeclaredInSpec,
    declaredRootModule = declaredRootModule,
  )
  errorSink.emit(
    DslTestPluginDependencyError(
      context = testPluginSpec.pluginId.value,
      testPluginId = testPluginSpec.pluginId,
      productName = productName,
      dependencyKind = DslTestPluginDependencyKind.CONTENT_MODULE,
      contentModuleDependencyId = depName,
      owningPlugins = disallowedOwners.mapTo(LinkedHashSet()) {
        DslTestPluginOwner(targetName = it.name, pluginId = it.pluginId)
      },
      dependencySource = dependencySource,
    )
  )
}
