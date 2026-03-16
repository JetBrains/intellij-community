// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.validator

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.ContentModuleNode
import com.intellij.platform.pluginGraph.EDGE_ALLOWS_MISSING
import com.intellij.platform.pluginGraph.GraphScope
import com.intellij.platform.pluginGraph.ProductNode
import com.intellij.platform.pluginGraph.containsEdge

internal fun GraphScope.collectMissingModuleDependencies(
    productId: Int,
    modulesToValidate: Iterable<ContentModuleNode>,
): Map<ContentModuleName, MutableSet<ContentModuleName>> {
  val missingDeps = HashMap<ContentModuleName, MutableSet<ContentModuleName>>()

  for (contentModule in modulesToValidate) {
    val moduleName = contentModule.name()
    val isCritical = contentModule.isCritical()

    contentModule.transitiveDeps { dep ->
      if (containsEdge(EDGE_ALLOWS_MISSING, productId, dep.id)) {
        return@transitiveDeps
      }
      if (ProductNode(productId).containsAvailableContentModule(dep)) {
        return@transitiveDeps
      }
      if (!isCritical && hasContentSource(dep.id)) {
        return@transitiveDeps
      }
      missingDeps.computeIfAbsent(dep.name()) { HashSet() }.add(moduleName)
    }
  }

  return missingDeps
}
