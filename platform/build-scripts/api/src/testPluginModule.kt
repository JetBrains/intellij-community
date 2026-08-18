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
 * production build is unaffected by the naming rules below.
 */
fun isTestOnlyPluginModule(moduleName: String, module: JpsModule?, outputProvider: ModuleOutputProvider): Boolean {
  val resolvedModule = module ?: outputProvider.findModule(moduleName)
  if (resolvedModule == null || !outputProvider.isTestCompilationOutputEnabled(resolvedModule)) {
    return false
  }

  // todo use some marker
  if (moduleName == "intellij.rdct.testFramework" ||
      moduleName == "intellij.platform.split.testFramework" ||
      moduleName == "intellij.python.junit5Tests" ||
      moduleName == "intellij.rdct.tests.distributed") {
    return true
  }

  // modules containing tests only as per https://youtrack.jetbrains.com/articles/IJPL-A-62
  if (moduleName.endsWith(".tests")) {
    return true
  }

  if (moduleName.contains(".test.")) {
    @Suppress("RedundantIf", "RedundantSuppression")
    if (resolvedModule.sourceRoots.none { it.rootType.isForTests }) {
      return false
    }

    return moduleName != "intellij.rider.test.framework" &&
           moduleName != "intellij.rider.test.build.shared" &&
           moduleName != "intellij.rider.test.framework.core" &&
           moduleName != "intellij.rider.test.framework.perforator" &&
           moduleName != "intellij.rider.test.framework.testng" &&
           moduleName != "intellij.rider.test.framework.junit" &&
           moduleName != "intellij.rider.test.framework.unit" &&
           moduleName != "intellij.rider.test.framework.integration.testng" &&
           moduleName != "intellij.rider.test.framework.integration.junit"
  }
  return moduleName.endsWith("._test")
}
