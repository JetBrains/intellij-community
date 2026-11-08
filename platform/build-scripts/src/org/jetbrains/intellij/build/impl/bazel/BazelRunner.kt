// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.bazel

import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.io.runProcess
import kotlin.io.path.exists
import kotlin.io.path.pathString

/**
 * Runs `bazel.cmd build` of [targets] in the project home of [buildContext], for a build that does not run under Bazel.
 * The outputs are in `out/bazel-bin` of the project home.
 */
@Internal
fun runBazelBuild(targets: List<String>, additionalArguments: List<String>, buildContext: BuildContext) {
  val projectHome = buildContext.paths.projectHome
  val bazelExecutable = projectHome.resolve("bazel.cmd")
  require(bazelExecutable.exists()) { "Bazel executable not found at ${bazelExecutable.pathString}" }
  val args = mutableListOf(
    bazelExecutable.pathString,
    "build",
  )
  args.addAll(targets)
  args.addAll(additionalArguments)
  runProcess(args, projectHome)
}