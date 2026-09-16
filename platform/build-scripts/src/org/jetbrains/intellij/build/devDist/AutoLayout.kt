// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.devDist

import org.jetbrains.annotations.ApiStatus
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
 * The derivation owns this copy of the rule, so that the packaging gate compares two producers. The build side
 * spells it in `inferredAutoLayoutChildren`, and the gate keeps the two in step.
 *
 * Sorted, the order the table states a layout member in.
 */
@ApiStatus.Internal
fun autoLayoutChildren(module: JpsModule, isPackedElsewhere: (String) -> Boolean): List<String> {
  val childPrefix = module.name.removeSuffix(".plugin") + "."
  val result = TreeSet<String>()
  for (dependency in module.getProductionModuleDependencies()) {
    val name = dependency.moduleReference.moduleName
    if (name.startsWith(childPrefix) && !isPackedElsewhere(name)) {
      result.add(name)
    }
  }
  return result.toList()
}
