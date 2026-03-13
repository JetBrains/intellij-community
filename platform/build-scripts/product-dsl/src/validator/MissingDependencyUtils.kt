// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.validator

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.ContentModuleNode
import com.intellij.platform.pluginGraph.EDGE_ALLOWS_MISSING
import com.intellij.platform.pluginGraph.GraphScope
import com.intellij.platform.pluginGraph.ProductNode
import com.intellij.platform.pluginGraph.containsEdge
import org.jetbrains.intellij.build.productLayout.debug

internal fun GraphScope.collectMissingModuleDependencies(
    product: ProductNode,
    modulesToValidate: Iterable<ContentModuleNode>,
): Map<ContentModuleName, MutableSet<ContentModuleName>> {
  val productName by lazy { product.name() }
  val missingDeps = HashMap<ContentModuleName, MutableSet<ContentModuleName>>()

  for (contentModule in modulesToValidate) {
    val moduleName = contentModule.name()
    val isCritical = contentModule.isCritical()

    contentModule.transitiveDeps { dep ->
      val depName = dep.name()
      if (containsEdge(EDGE_ALLOWS_MISSING, product.id, dep.id)) {
        debug("missingDeps") {
          "skip reason=allowMissing product=$productName source=${moduleName.value} dep=${depName.value} critical=$isCritical"
        }
        return@transitiveDeps
      }
      if (product.containsAvailableContentModule(dep)) {
        debug("missingDeps") {
          "skip reason=availableInProduct product=$productName source=${moduleName.value} dep=${depName.value} critical=$isCritical"
        }
        return@transitiveDeps
      }
      if (!isCritical && hasContentSource(dep.id)) {
        debug("missingDeps") {
          "skip reason=nonCriticalAndHasSource product=$productName source=${moduleName.value} dep=${depName.value}"
        }
        return@transitiveDeps
      }
      missingDeps.computeIfAbsent(depName) { HashSet() }.add(moduleName)
      debug("missingDeps") {
        "missing product=$productName source=${moduleName.value} dep=${depName.value} critical=$isCritical"
      }
    }
  }

  return missingDeps
}
