// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.devDist

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.impl.moduleBased.ModuleRootMatcher
import org.jetbrains.jps.model.module.JpsModule

/**
 * The frontend module filter, over the JPS model and the root modules a frontend-compatible module must not reach.
 *
 * A module is compatible with the frontend when neither it nor any module it reaches through its production runtime
 * dependencies is one of [roots]. [ModuleRootMatcher] holds that walk, and the packer asks the same walk. The derivation
 * chooses [roots] itself, so the packaging gate still compares the derivation's choice against the product's filter.
 *
 * Nothing is compatible when [roots] is empty. That is the answer a product without an embedded frontend root module
 * gets from the build's empty filter.
 */
@ApiStatus.Internal
class FrontendCompatibility(
  @JvmField val roots: Set<String>,
  private val findModule: (String) -> JpsModule?,
) {
  private val matcher = ModuleRootMatcher(roots)

  fun isCompatible(moduleName: String): Boolean {
    if (roots.isEmpty()) {
      return false
    }
    val module = findModule(moduleName) ?: return false
    return matcher.matches(module)
  }

  /** Whether [member] is compatible with the frontend while [mainModule] is not. */
  fun isSplit(mainModule: String, member: String): Boolean {
    return isCompatible(member) && !isCompatible(mainModule)
  }
}
