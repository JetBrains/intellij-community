// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.generator

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.PluginGraph
import com.intellij.platform.pluginGraph.PluginId
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

internal data class ExistingDependencyHandling<T>(
  val effectiveSuppressedDeps: Set<T>,
  val preserveExistingDeps: Set<T>,
)

internal fun computeAliasPreservedPluginDeps(
  graph: PluginGraph,
  existingXmlPluginDeps: Set<PluginId>,
): Set<PluginId> {
  if (existingXmlPluginDeps.isEmpty()) {
    return emptySet()
  }
  return graph.query {
    existingXmlPluginDeps.filterTo(LinkedHashSet()) { dep -> hasAliasPlugin(dep) }
  }
}

internal fun <T> computeExistingDependencyHandling(
  updateSuppressions: Boolean,
  existingXmlDeps: Set<T>,
  jpsDeps: Set<T>,
  suppressedDeps: Set<T>,
  semanticallyPreservedExistingDeps: Set<T> = emptySet(),
  xmlOnlySuppressionCandidateDeps: Set<T> = existingXmlDeps,
): ExistingDependencyHandling<T> {
  val preservedWithoutSuppression = semanticallyPreservedExistingDeps.filterTo(LinkedHashSet()) { it in existingXmlDeps }
  val suppressionRelevantDeps = suppressedDeps.filterTo(LinkedHashSet()) { it !in preservedWithoutSuppression }
  if (!updateSuppressions) {
    return ExistingDependencyHandling(
      effectiveSuppressedDeps = suppressionRelevantDeps,
      preserveExistingDeps = existingXmlDeps.filterTo(LinkedHashSet()) { it in suppressionRelevantDeps || it in preservedWithoutSuppression },
    )
  }
  val missingInXml = jpsDeps.filterNotTo(LinkedHashSet()) { it in existingXmlDeps }
  val xmlOnly = xmlOnlySuppressionCandidateDeps.filterNotTo(LinkedHashSet()) {
    it in jpsDeps || it in preservedWithoutSuppression
  }
  val effectiveSuppressedDeps = suppressionRelevantDeps + missingInXml + xmlOnly
  return ExistingDependencyHandling(
    effectiveSuppressedDeps = effectiveSuppressedDeps,
    preserveExistingDeps = existingXmlDeps.filterTo(LinkedHashSet()) { it in effectiveSuppressedDeps || it in preservedWithoutSuppression },
  )
}
