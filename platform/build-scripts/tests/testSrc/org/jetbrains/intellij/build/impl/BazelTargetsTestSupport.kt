// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

/** A module of `bazel-targets.json` with production jars only. [targets] and [jars] are parallel lists. */
internal fun moduleDescription(targets: List<String>, jars: List<String>): BazelTargetsInfo.TargetsFileModuleDescription {
  return BazelTargetsInfo.TargetsFileModuleDescription(
    productionTargets = targets,
    productionJars = jars,
    testTargets = emptyList(),
    testJars = emptyList(),
    exports = emptyList(),
    moduleLibraries = emptyMap(),
  )
}
