// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl.moduleBased

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsModule
import java.util.concurrent.ConcurrentHashMap

/**
 * Whether a module reaches none of [incompatibleRootModules] through its production runtime dependencies.
 *
 * The one walk of the product-mode match. [JpsProductModeMatcher] asks it for the packer, and the dev-distribution
 * derivation asks it through `FrontendCompatibility`. Each caller chooses the roots itself. Concurrent callers may share
 * one instance.
 */
@ApiStatus.Internal
class ModuleRootMatcher(private val incompatibleRootModules: Set<String>) {
  // Cache only final results. Provisional cycle shortcuts must stay local to a single traversal.
  private val cache = ConcurrentHashMap<String, Boolean>()

  fun matches(module: JpsModule): Boolean {
    return matches(module = module, visiting = HashSet(), evaluation = MatchEvaluation())
  }

  private fun matches(module: JpsModule, visiting: HashSet<String>, evaluation: MatchEvaluation): Boolean {
    val moduleName = module.name
    val cached = cache.get(moduleName)
    if (cached != null) {
      return cached
    }

    if (incompatibleRootModules.contains(moduleName)) {
      cache.put(moduleName, false)
      return false
    }

    if (!visiting.add(moduleName)) {
      // A back edge is compatible unless another path proves otherwise, but it makes positive results incomplete.
      evaluation.isComplete = false
      return true
    }
    var matches = true
    try {
      JpsJavaExtensionService.dependencies(module).productionOnly().runtimeOnly().forEachModule {
        if (matches) {
          matches = matches(module = it, visiting = visiting, evaluation = evaluation)
        }
      }
    }
    finally {
      visiting.remove(moduleName)
    }

    // A negative result is final immediately; a positive result is final only when no dependency edge was skipped as a cycle.
    if (!matches || evaluation.isComplete) {
      cache.put(moduleName, matches)
    }
    return matches
  }

  private class MatchEvaluation {
    // Tracks whether all visited dependency edges were fully evaluated for the current top-level match call.
    @JvmField var isComplete: Boolean = true
  }
}
