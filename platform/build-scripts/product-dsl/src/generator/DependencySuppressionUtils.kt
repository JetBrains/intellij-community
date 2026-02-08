// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.generator

import com.intellij.platform.pluginGraph.ContentModuleName
import org.jetbrains.intellij.build.productLayout.stats.SuppressionType
import org.jetbrains.intellij.build.productLayout.stats.SuppressionUsage

internal fun collectModuleDepsWithSuppressions(
  contentModuleName: ContentModuleName,
  dependencies: Iterable<ContentModuleName>,
  suppressedModules: Set<ContentModuleName>,
  suppressionUsages: MutableList<SuppressionUsage>,
): List<String> {
  val moduleDeps = ArrayList<String>()
  for (depModule in dependencies) {
    val depName = depModule.value
    if (suppressedModules.contains(depModule)) {
      suppressionUsages.add(SuppressionUsage(contentModuleName, depName, SuppressionType.MODULE_DEP))
    }
    else {
      moduleDeps.add(depName)
    }
  }
  return moduleDeps
}

internal fun <T> computeEffectiveSuppressedDeps(
  updateSuppressions: Boolean,
  existingXmlDeps: Set<T>,
  jpsDeps: Set<T>,
  suppressedDeps: Set<T>,
): Set<T> {
  if (!updateSuppressions) {
    return suppressedDeps
  }
  val missingInXml = jpsDeps.filterNotTo(LinkedHashSet()) { it in existingXmlDeps }
  val xmlOnly = existingXmlDeps.filterNotTo(LinkedHashSet()) { it in jpsDeps }
  return suppressedDeps + missingInXml + xmlOnly
}
