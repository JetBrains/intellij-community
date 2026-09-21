// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.jetbrains.jps.model.module.JpsModule

/**
 * Whether [moduleName] contributes to a distribution through its *test* compilation output only.
 *
 * Such a module has no production payload at all - its Bazel production target is an empty stub, and its
 * descriptor is a test resource - so both packing it and reading a descriptor out of it must go to test output.
 * The two used to decide this separately, and descriptor search got it wrong: it probed production output first,
 * which under an explicit Bazel input manifest *declares* that stub jar as a fragment input before a byte is read.
 *
 * Always false unless [ModuleOutputProvider.isTestCompilationOutputEnabled] allows this module's test output, so a
 * production build is unaffected by the name rule of [isTestOnlyPluginModuleName].
 */
fun isTestOnlyPluginModule(moduleName: String, module: JpsModule?, outputProvider: ModuleOutputProvider): Boolean {
  val resolvedModule = module ?: outputProvider.findModule(moduleName)
  if (resolvedModule == null || !outputProvider.isTestCompilationOutputEnabled(resolvedModule)) {
    return false
  }
  return isTestOnlyPluginModuleName(moduleName = moduleName, module = resolvedModule)
}

/**
 * The rule of [isTestOnlyPluginModule], without its enablement check.
 *
 * True when every source root of [module] is a test root. True for a `.tests` suffix. True for a `.test.` segment when
 * [module] has a test source root. A generator that plans a test plugin applies this rule to match the packager's
 * output-root choice.
 */
fun isTestOnlyPluginModuleName(moduleName: String, module: JpsModule): Boolean {
  // a module without production source roots has test output only
  if (module.sourceRoots.isNotEmpty() && module.sourceRoots.all { it.rootType.isForTests }) {
    return true
  }

  // modules containing tests only as per https://youtrack.jetbrains.com/articles/IJPL-A-62
  if (moduleName.endsWith(".tests")) {
    return true
  }

  if (moduleName.contains(".test.")) {
    return module.sourceRoots.any { it.rootType.isForTests }
  }
  return false
}
