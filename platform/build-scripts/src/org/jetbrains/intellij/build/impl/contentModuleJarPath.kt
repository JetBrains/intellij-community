// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import org.jetbrains.annotations.ApiStatus

/**
 * The jar of the plugin content module [moduleName], relative to the plugin's `lib/`, or `null` when the layout places
 * the module by a path of its own.
 *
 * This is the one jar-path rule. The packer applies it in `autoLayout.kt`, and the dev-distribution derivation applies
 * it in its projection and its candidacy. Each caller reads the facts itself: the packer from the patched descriptor and
 * the product's frontend filter, the derivation from the source descriptor and its own model walk. The packaging gate
 * compares the two outputs, so it still catches a wrong fact, and the rule itself cannot drift between them.
 *
 * An embedded module gets `<module>.jar`. Any other module gets `modules/<module>.jar` when its descriptor declares no
 * `package` or [packedIntoSeparateJar] holds; the main jar cannot load it then. A module that stays is co-packed into
 * [mainJarName], or into `<main>-frontend.jar` for a frontend member of a plugin whose main module is not one. A module
 * with [hasCustomPath] gets no path from this rule. The facts are functions, so a caller reads only what the rule asks.
 */
@ApiStatus.Internal
fun contentModuleJarPath(
  moduleName: String,
  loadingRule: String?,
  hasCustomPath: Boolean,
  mainJarName: String,
  hasPackageAttribute: () -> Boolean,
  packedIntoSeparateJar: () -> Boolean,
  frontendSplit: () -> Boolean,
): String? {
  if (loadingRule == "embedded") {
    return if (hasCustomPath) null else "$moduleName.jar"
  }
  return when {
    !hasPackageAttribute() || packedIntoSeparateJar() -> "modules/$moduleName.jar"
    hasCustomPath -> null
    frontendSplit() -> mainJarName.removeSuffix(".jar") + "-frontend.jar"
    else -> mainJarName
  }
}
