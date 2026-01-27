// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.benchmark.scenarios

import java.nio.file.Path

/**
 * Scenario that tests cold build performance (no cached state).
 *
 * This scenario performs a `bazel clean` before each measurement to ensure
 * both incremental and non-incremental modes start from scratch.
 *
 * Expected behavior: Both modes should perform similarly since there's
 * no existing state to leverage for incremental compilation.
 */
class ColdBuildScenario(
  projectPath: Path,
  private val bazelPath: String = "bazel",
) : BaseScenario(projectPath) {

  override val name: String = "cold_build"

  override val description: String = "Initial build with no cached state"

  private var cleanPerformed = false

  override fun setup() {
    // Perform a bazel clean to ensure cold start
    val process = ProcessBuilder(bazelPath, "clean")
      .directory(projectPath.toFile())
      .inheritIO()
      .start()
    cleanPerformed = process.waitFor() == 0
  }

  override fun cleanup() {
    // Nothing to cleanup - clean was the setup
    cleanPerformed = false
  }

  override fun expectedBehavior(): String =
    "Both modes similar (no cache to leverage)"
}
