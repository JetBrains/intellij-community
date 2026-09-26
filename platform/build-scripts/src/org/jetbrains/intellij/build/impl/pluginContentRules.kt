// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.model.module.JpsLibraryDependency
import org.jetbrains.jps.model.module.JpsModuleReference

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
    else -> pluginDefaultJarName(mainJarName, frontendSplit())
  }
}

/**
 * The jar a plugin co-packs a module into: [mainJarName], or `<main>-frontend.jar` for a frontend member of a plugin
 * whose main module is not frontend-compatible, see [frontendSplit].
 */
@ApiStatus.Internal
fun pluginDefaultJarName(mainJarName: String, frontendSplit: Boolean): String {
  return if (frontendSplit) mainJarName.removeSuffix(".jar") + "-frontend.jar" else mainJarName
}

/**
 * Whether the direct dependency [moduleName] of an `auto` plugin layout is a child the layout packs: its name extends
 * the name of [mainModule] without the `.plugin` suffix. A caller still drops a child the platform or another plugin
 * layout packs, over the facts it reads itself.
 */
@ApiStatus.Internal
fun isAutoLayoutChild(mainModule: String, moduleName: String): Boolean {
  return moduleName.startsWith(mainModule.removeSuffix(".plugin") + ".")
}

/**
 * Whether a content module brings a module library of its own into its jar, which sends the module to a jar of its own.
 *
 * [productionLibraryDependencies] are the production runtime library dependencies of the module. [librariesKeptOut]
 * holds when the layout keeps the module libraries out of the module's jar, see `doNotCopyModuleLibrariesAutomatically`.
 * The packer and the dev-distribution derivation both ask this one predicate over the facts each reads itself.
 */
@ApiStatus.Internal
fun hasOwnModuleLibraries(productionLibraryDependencies: List<JpsLibraryDependency>, librariesKeptOut: Boolean): Boolean {
  return !librariesKeptOut && productionLibraryDependencies.any { it.libraryReference.parentReference is JpsModuleReference }
}
