// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.devDist

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsModule

/**
 * The frontend module filter, over the JPS model and the root modules a frontend-compatible module must not reach.
 *
 * A module is compatible with the frontend when neither it nor any module it reaches through its production runtime
 * dependencies is one of [roots]. The derivation owns this copy of the rule, so that the packaging gate compares two
 * producers. The build side answers the same question through its own filter, and the gate keeps the two in step.
 *
 * Nothing is compatible when [roots] is empty. That is the answer a product without an embedded frontend root module
 * gets from the build's empty filter.
 */
@ApiStatus.Internal
class FrontendCompatibility(
  private val roots: Set<String>,
  private val findModule: (String) -> JpsModule?,
) {
  // Only a final result is cached. A back edge inside one walk is compatible unless another path proves otherwise, and
  // such a positive result must stay local to that walk.
  private val cache = HashMap<String, Boolean>()

  fun isCompatible(moduleName: String): Boolean {
    if (roots.isEmpty()) {
      return false
    }
    val module = findModule(moduleName) ?: return false
    return matches(module = module, visiting = HashSet(), evaluation = Evaluation())
  }

  /** Whether [member] is compatible with the frontend while [mainModule] is not. */
  fun isSplit(mainModule: String, member: String): Boolean {
    return isCompatible(member) && !isCompatible(mainModule)
  }

  private fun matches(module: JpsModule, visiting: HashSet<String>, evaluation: Evaluation): Boolean {
    val name = module.name
    cache.get(name)?.let { return it }
    if (name in roots) {
      cache.put(name, false)
      return false
    }
    if (!visiting.add(name)) {
      evaluation.complete = false
      return true
    }
    val compatible = try {
      JpsJavaExtensionService.dependencies(module).productionOnly().runtimeOnly().modules.all {
        matches(module = it, visiting = visiting, evaluation = evaluation)
      }
    }
    finally {
      visiting.remove(name)
    }
    if (!compatible || evaluation.complete) {
      cache.put(name, compatible)
    }
    return compatible
  }

  private class Evaluation {
    @JvmField var complete: Boolean = true
  }
}
