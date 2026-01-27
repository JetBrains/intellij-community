// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.benchmark.scenarios

import java.nio.file.Path

/**
 * Scenario that tests no-op rebuild performance (no source changes).
 *
 * This scenario assumes a previous build has completed and measures
 * how quickly each mode can determine nothing needs to be done.
 *
 * Expected behavior: Incremental mode should be nearly instant (just
 * checks dependency graph), while non-incremental may do more work.
 */
class NoOpScenario(
  projectPath: Path,
) : BaseScenario(projectPath) {

  override val name: String = "no_op"

  override val description: String = "Rebuild with no source changes"

  override fun setup() {
    // Nothing to set up - we want unchanged state
    // The benchmark runner should ensure a build has been done first
  }

  override fun cleanup() {
    // Nothing to cleanup
  }

  override fun expectedBehavior(): String =
    "Incremental = instant, Non-incremental = full rebuild check"
}
