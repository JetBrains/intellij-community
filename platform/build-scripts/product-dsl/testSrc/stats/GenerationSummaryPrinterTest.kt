// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.stats

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Pins the dev-distribution plan report in the generation summary.
 *
 * The summary reads the plan result off [GenerationStats]. A summary that receives no plan result cannot name a plan
 * file the run rewrote, and it reports `All files unchanged` instead.
 *
 * These tests prove the summary names the plan file, and that it asks for a commit only when a run wrote something.
 *
 * [AnsiColors] always emits escape codes, so each assertion uses a fragment that holds no code.
 */
class GenerationSummaryPrinterTest {
  @Test
  fun `a committing run names the plan file it wrote`() {
    // A committing run with one modified plan file.
    val text = summaryOf(
      stats = devDistPlanStats(
        DevDistPlanFileResult("build/dev_dist_fragment_inputs.bzl", FileChangeStatus.MODIFIED),
        DevDistPlanFileResult("build/dev_dist_module_sets.bzl", FileChangeStatus.UNCHANGED),
        DevDistPlanFileResult("build/dev_dist_content_sets.bzl", FileChangeStatus.UNCHANGED),
      ),
      committed = true,
    )

    assertThat(text).contains("Dev-Dist Plan")
    assertThat(text).contains("dev_dist_fragment_inputs.bzl")
    assertThat(text).contains("Commit the change")
    assertThat(text).doesNotContain("All files unchanged")
  }

  @Test
  fun `a committing run with no change counts the plan files`() {
    val text = summaryOf(
      stats = devDistPlanStats(
        DevDistPlanFileResult("build/dev_dist_fragment_inputs.bzl", FileChangeStatus.UNCHANGED),
        DevDistPlanFileResult("build/dev_dist_module_sets.bzl", FileChangeStatus.UNCHANGED),
      ),
      committed = true,
    )

    assertThat(text).contains("All files unchanged")
    assertThat(text).contains("Dev-dist plan:")
    assertThat(text).doesNotContain("Commit the change")
  }

  @Test
  fun `a validating run prints no commit hint`() {
    // A validating run writes nothing, so a request to commit would point at no change.
    val text = summaryOf(
      stats = devDistPlanStats(
        DevDistPlanFileResult("build/dev_dist_fragment_inputs.bzl", FileChangeStatus.MODIFIED),
      ),
      committed = false,
    )

    assertThat(text).contains("dev_dist_fragment_inputs.bzl")
    assertThat(text).doesNotContain("Commit the change")
  }
}

/**
 * Builds a [GenerationStats] that holds only a dev-distribution plan result. Every other result stays empty, so a test
 * reads as one fact about the plan report.
 */
private fun devDistPlanStats(vararg files: DevDistPlanFileResult): GenerationStats {
  return GenerationStats(
    moduleSetResults = emptyList(),
    dependencyResult = null,
    contentModuleResult = null,
    pluginDependencyResult = null,
    productResult = null,
    devDistPlanResult = DevDistPlanGenerationResult(files = files.toList()),
    durationMs = 1_000,
  )
}

/**
 * Returns the summary text the printer builds for the stats.
 */
private fun summaryOf(stats: GenerationStats, committed: Boolean): String {
  return buildGenerationSummary(stats = stats, committed = committed)
}
