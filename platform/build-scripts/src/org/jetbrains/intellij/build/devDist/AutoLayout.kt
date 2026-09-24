// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.devDist

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.impl.isAutoLayoutChild
import org.jetbrains.intellij.build.productLayout.util.getProductionModuleDependencies
import org.jetbrains.jps.model.module.JpsModule
import java.util.TreeSet

/**
 * The members an `auto` layout packs beside the plugin's `<content>` and its `withModule` items.
 *
 * The rule reads the direct production dependencies of the main module [module]. It takes a dependency whose name
 * starts with the main module name without the `.plugin` suffix, and drops one the platform or another plugin layout
 * packs, which [isPackedElsewhere] answers. Each child goes into the main jar, or into the `-frontend.jar` of a
 * plugin whose main module is not frontend-compatible while the child is.
 *
 * [isAutoLayoutChild] states which dependency is a child, and the build side asks it in `inferredAutoLayoutChildren`
 * too. The derivation reads the rest itself: the dependencies, and what [isPackedElsewhere] answers.
 *
 * Sorted, the order the table states a layout member in.
 */
@ApiStatus.Internal
fun autoLayoutChildren(module: JpsModule, isPackedElsewhere: (String) -> Boolean): List<String> {
  val result = TreeSet<String>()
  for (dependency in module.getProductionModuleDependencies()) {
    val name = dependency.moduleReference.moduleName
    if (isAutoLayoutChild(mainModule = module.name, moduleName = name) && !isPackedElsewhere(name)) {
      result.add(name)
    }
  }
  return result.toList()
}
