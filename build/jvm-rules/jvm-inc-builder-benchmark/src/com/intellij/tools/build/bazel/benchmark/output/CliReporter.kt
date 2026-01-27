// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.benchmark.output

import com.intellij.tools.build.bazel.benchmark.runner.AggregatedMetrics
import com.intellij.tools.build.bazel.benchmark.runner.BenchmarkResult
import com.intellij.tools.build.bazel.benchmark.runner.ScenarioResult

/**
 * Produces formatted CLI output for benchmark results.
 */
class CliReporter {

  /**
   * Formats benchmark results as a pretty table for terminal output.
   */
  fun formatResult(result: BenchmarkResult): String {
    return buildString {
      appendLine()
      appendLine("=== JVM Incremental Compilation Benchmark ===")
      appendLine()
      appendLine("Project: ${result.projectInfo.name} (${result.projectInfo.fileCount} files, ${result.projectInfo.language.name.lowercase()})")
      appendLine("Iterations: ${result.config.measurementIterations} (${result.config.warmupIterations} warmup)")
      appendLine()

      // Table header
      val scenarioWidth = 14
      val incWidth = 14
      val nonIncWidth = 14
      val speedupWidth = 9

      appendLine(formatTableLine(scenarioWidth, incWidth, nonIncWidth, speedupWidth))
      appendLine(formatHeader(scenarioWidth, incWidth, nonIncWidth, speedupWidth))
      appendLine(formatTableLine(scenarioWidth, incWidth, nonIncWidth, speedupWidth))

      // Table rows
      result.scenarioResults.forEach { scenario ->
        appendLine(formatRow(scenario, scenarioWidth, incWidth, nonIncWidth, speedupWidth))
      }

      appendLine(formatTableLine(scenarioWidth, incWidth, nonIncWidth, speedupWidth))
      appendLine()

      // Summary
      val avgSpeedup = result.scenarioResults.map { it.speedup }.average()
      appendLine("Average speedup: ${formatSpeedup(avgSpeedup)}")
      appendLine()
    }
  }

  private fun formatTableLine(scenarioWidth: Int, incWidth: Int, nonIncWidth: Int, speedupWidth: Int): String {
    return "+" + "-".repeat(scenarioWidth) + "+" + "-".repeat(incWidth) + "+" +
      "-".repeat(nonIncWidth) + "+" + "-".repeat(speedupWidth) + "+"
  }

  private fun formatHeader(scenarioWidth: Int, incWidth: Int, nonIncWidth: Int, speedupWidth: Int): String {
    return "| " + "Scenario".padEnd(scenarioWidth - 2) + " | " +
      "Incremental".padEnd(incWidth - 2) + " | " +
      "Non-Inc".padEnd(nonIncWidth - 2) + " | " +
      "Speedup".padEnd(speedupWidth - 2) + " |"
  }

  private fun formatRow(
    scenario: ScenarioResult,
    scenarioWidth: Int,
    incWidth: Int,
    nonIncWidth: Int,
    speedupWidth: Int
  ): String {
    val name = scenario.scenarioName.replace("_", " ").take(scenarioWidth - 3)
    val incTime = formatTime(scenario.incrementalMetrics)
    val nonIncTime = formatTime(scenario.nonIncrementalMetrics)
    val speedup = formatSpeedup(scenario.speedup)

    return "| " + name.padEnd(scenarioWidth - 2) + " | " +
      incTime.padEnd(incWidth - 2) + " | " +
      nonIncTime.padEnd(nonIncWidth - 2) + " | " +
      speedup.padEnd(speedupWidth - 2) + " |"
  }

  private fun formatTime(metrics: AggregatedMetrics): String {
    val mean = metrics.meanMs
    val stdDev = metrics.stdDevMs

    return when {
      mean >= 60000 -> "${mean / 60000}m${(mean % 60000) / 1000}s"
      mean >= 1000 -> "${mean / 1000}.${(mean % 1000) / 100}s \u00b1${stdDev / 1000}.${(stdDev % 1000) / 100}s"
      else -> "${mean}ms \u00b1${stdDev}ms"
    }
  }

  private fun formatSpeedup(speedup: Double): String {
    return String.format("%.2fx", speedup)
  }

  /**
   * Formats a comparison between two benchmark results.
   */
  fun formatComparison(baseline: BenchmarkResult, current: BenchmarkResult): String {
    return buildString {
      appendLine()
      appendLine("=== Benchmark Comparison ===")
      appendLine()
      appendLine("Baseline: ${baseline.timestamp}")
      appendLine("Current:  ${current.timestamp}")
      appendLine()

      val baselineMap = baseline.scenarioResults.associateBy { it.scenarioName }

      val scenarioWidth = 14
      val baseIncWidth = 12
      val currIncWidth = 12
      val changeWidth = 10

      appendLine("+" + "-".repeat(scenarioWidth) + "+" + "-".repeat(baseIncWidth) + "+" +
        "-".repeat(currIncWidth) + "+" + "-".repeat(changeWidth) + "+")
      appendLine("| " + "Scenario".padEnd(scenarioWidth - 2) + " | " +
        "Baseline".padEnd(baseIncWidth - 2) + " | " +
        "Current".padEnd(currIncWidth - 2) + " | " +
        "Change".padEnd(changeWidth - 2) + " |")
      appendLine("+" + "-".repeat(scenarioWidth) + "+" + "-".repeat(baseIncWidth) + "+" +
        "-".repeat(currIncWidth) + "+" + "-".repeat(changeWidth) + "+")

      current.scenarioResults.forEach { currentScenario ->
        val baselineScenario = baselineMap[currentScenario.scenarioName]
        if (baselineScenario != null) {
          val baseMean = baselineScenario.incrementalMetrics.meanMs
          val currMean = currentScenario.incrementalMetrics.meanMs
          val change = if (baseMean > 0) ((currMean - baseMean).toDouble() / baseMean) * 100 else 0.0
          val changeStr = String.format("%+.1f%%", change)

          appendLine("| " + currentScenario.scenarioName.take(scenarioWidth - 3).padEnd(scenarioWidth - 2) + " | " +
            "${baseMean}ms".padEnd(baseIncWidth - 2) + " | " +
            "${currMean}ms".padEnd(currIncWidth - 2) + " | " +
            changeStr.padEnd(changeWidth - 2) + " |")
        }
      }

      appendLine("+" + "-".repeat(scenarioWidth) + "+" + "-".repeat(baseIncWidth) + "+" +
        "-".repeat(currIncWidth) + "+" + "-".repeat(changeWidth) + "+")
      appendLine()
    }
  }

  /**
   * Prints progress during benchmark execution.
   */
  fun printProgress(scenarioName: String, iteration: Int, total: Int, isWarmup: Boolean) {
    val prefix = if (isWarmup) "[Warmup]" else "[Measure]"
    print("\r$prefix $scenarioName: $iteration/$total")
    System.out.flush()
  }

  /**
   * Clears the progress line.
   */
  fun clearProgress() {
    print("\r" + " ".repeat(60) + "\r")
    System.out.flush()
  }
}
