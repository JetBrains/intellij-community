// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.bazel

import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.io.runProcess
import kotlin.io.path.exists
import kotlin.io.path.pathString

internal suspend fun runBazelBuild(targets: List<String>, buildContext: BuildContext) {
  val projectHome = buildContext.paths.projectHome
  val bazelExecutable = projectHome.resolve("bazel.cmd")
  require(bazelExecutable.exists()) { "Bazel executable not found at ${bazelExecutable.pathString}" }
  val args = mutableListOf(
    bazelExecutable.pathString,
    "build",
  )
  args.addAll(targets)
  runProcess(args, projectHome)
}