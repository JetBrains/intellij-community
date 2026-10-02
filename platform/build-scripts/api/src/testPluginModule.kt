// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.jetbrains.jps.model.module.JpsModule

/**
 * The monorepo keeps the production roots and the test roots of a feature in separate modules, see
 * `IdeaUltimateProjectStructureTest`, so a module with a test root has test output only.
 */
fun JpsModule.isTestModule(): Boolean = sourceRoots.any { it.rootType.isForTests }

/**
 * Whether [moduleName] contributes to a distribution through its *test* compilation output only.
 *
 * Such a module has no production payload at all - its Bazel production target is an empty stub, and its
 * descriptor is a test resource - so both packing it and reading a descriptor out of it must go to test output.
 * The two used to decide this separately, and descriptor search got it wrong: it probed production output first,
 * which under an explicit Bazel input manifest *declares* that stub jar as a fragment input before a byte is read.
 *
 * Always false unless [ModuleOutputProvider.isTestCompilationOutputEnabled] allows this module's test output, so a
 * production build is unaffected by [isTestModule].
 */
fun isTestOnlyPluginModule(moduleName: String, module: JpsModule?, outputProvider: ModuleOutputProvider): Boolean {
  val resolvedModule = module ?: outputProvider.findRequiredModule(moduleName)
  return outputProvider.isTestCompilationOutputEnabled(resolvedModule) && resolvedModule.isTestModule()
}
