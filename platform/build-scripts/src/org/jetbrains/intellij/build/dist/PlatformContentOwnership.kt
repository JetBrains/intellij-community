// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dist

import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.intellij.build.impl.ModuleIncludeReasons
import org.jetbrains.intellij.build.impl.ModuleItem

/**
 * Requires content ownership except for the exact ordered members of [retainedJars].
 * A jar in [optionalRetainedJars] counts as retained only when [modules] places a module in it.
 */
@Internal
@Suppress("ReplaceGetOrSet")
fun validatePlatformContentOwnership(
  modules: Collection<ModuleItem>,
  retainedJars: Map<String, List<String>>,
  explicitModuleNames: Collection<String> = emptyList(),
  optionalRetainedJars: Map<String, List<String>> = emptyMap(),
) {
  val presentJars = modules.mapTo(HashSet()) { it.relativeOutputFile }
  val effectiveRetainedJars = retainedJars + optionalRetainedJars.filterKeys { it in presentJars }
  val errors = ArrayList<String>()
  for ((name, owners) in modules.groupBy { it.moduleName }) {
    if (owners.size > 1) {
      errors.add("Duplicate platform ownership for $name: $owners")
    }
  }
  val contentModules = modules.filter { ModuleIncludeReasons.isProductModule(it.reason) }.mapTo(HashSet()) { it.moduleName }
  for (name in explicitModuleNames) {
    if (name in contentModules) {
      errors.add("Content module $name also has explicit platform ownership")
    }
  }
  val nonContentModules = modules.filterNot { ModuleIncludeReasons.isProductModule(it.reason) }
  for (item in nonContentModules) {
    if (effectiveRetainedJars.get(item.relativeOutputFile)?.contains(item.moduleName) != true) {
      errors.add("Missing content ownership: ${item.moduleName} in ${item.relativeOutputFile}; ${item.reason}")
    }
  }
  val actualJars = modules.groupBy({ it.relativeOutputFile }, { it.moduleName })
  val nonContentJars = nonContentModules.groupBy({ it.relativeOutputFile }, { it.moduleName })
  for ((path, members) in effectiveRetainedJars) {
    for (name in members) {
      if (name !in nonContentJars.get(path).orEmpty()) {
        errors.add("Stale content ownership exception: $name in $path")
      }
    }
    if (actualJars.get(path) != members) {
      errors.add("Changed retained jar $path: expected $members, actual ${actualJars.get(path)}")
    }
  }
  check(errors.isEmpty()) { errors.joinToString("\n") }
}
