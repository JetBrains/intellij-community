// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.benchmark.output

import com.intellij.tools.build.bazel.benchmark.runner.AggregatedMetrics
import com.intellij.tools.build.bazel.benchmark.runner.BenchmarkResult
import com.intellij.tools.build.bazel.benchmark.runner.ScenarioResult
import java.nio.file.Path
import java.time.format.DateTimeFormatter

/**
 * Produces JSON output for benchmark results.
 *
 * Output format:
 * ```json
 * {
 *   "timestamp": "2024-01-26T10:30:00Z",
 *   "config": { ... },
 *   "results": [ ... ]
 * }
 * ```
 */
class JsonReporter {

  /**
   * Writes benchmark results to a JSON file.
   */
  fun writeResults(result: BenchmarkResult, outputPath: Path) {
    val json = formatResult(result)
    outputPath.toFile().writeText(json)
  }

  /**
   * Formats benchmark results as JSON string.
   */
  fun formatResult(result: BenchmarkResult): String {
    return buildString {
      appendLine("{")
      appendLine("  \"timestamp\": \"${result.timestamp.toString()}\",")
      appendLine("  \"config\": {")
      appendLine("    \"project\": \"${result.projectInfo.name}\",")
      appendLine("    \"files\": ${result.projectInfo.fileCount},")
      appendLine("    \"language\": \"${result.projectInfo.language.name.lowercase()}\",")
      appendLine("    \"warmupIterations\": ${result.config.warmupIterations},")
      appendLine("    \"measurementIterations\": ${result.config.measurementIterations}")
      appendLine("  },")
      appendLine("  \"results\": [")

      result.scenarioResults.forEachIndexed { index, scenario ->
        append(formatScenarioResult(scenario, indent = 4))
        if (index < result.scenarioResults.size - 1) {
          appendLine(",")
        } else {
          appendLine()
        }
      }

      appendLine("  ]")
      appendLine("}")
    }
  }

  private fun formatScenarioResult(result: ScenarioResult, indent: Int): String {
    val pad = " ".repeat(indent)
    return buildString {
      appendLine("$pad{")
      appendLine("$pad  \"scenario\": \"${result.scenarioName}\",")
      appendLine("$pad  \"incremental\": ${formatMetrics(result.incrementalMetrics, indent + 2)},")
      appendLine("$pad  \"non_incremental\": ${formatMetrics(result.nonIncrementalMetrics, indent + 2)},")
      appendLine("$pad  \"speedup\": ${formatDouble(result.speedup)}")
      append("$pad}")
    }
  }

  private fun formatMetrics(metrics: AggregatedMetrics, indent: Int): String {
    val pad = " ".repeat(indent)
    return buildString {
      appendLine("{")
      appendLine("$pad  \"mean_ms\": ${metrics.meanMs},")
      appendLine("$pad  \"median_ms\": ${metrics.medianMs},")
      appendLine("$pad  \"std_dev_ms\": ${metrics.stdDevMs},")
      appendLine("$pad  \"min_ms\": ${metrics.minMs},")
      appendLine("$pad  \"max_ms\": ${metrics.maxMs},")
      appendLine("$pad  \"success_rate\": ${formatDouble(metrics.successRate)}")
      append("$pad}")
    }
  }

  private fun formatDouble(value: Double): String {
    return String.format("%.2f", value)
  }

  /**
   * Compares two benchmark results and produces a diff report.
   */
  fun compareResults(baseline: BenchmarkResult, current: BenchmarkResult): String {
    return buildString {
      appendLine("{")
      appendLine("  \"comparison\": {")
      appendLine("    \"baseline_timestamp\": \"${baseline.timestamp}\",")
      appendLine("    \"current_timestamp\": \"${current.timestamp}\"")
      appendLine("  },")
      appendLine("  \"diffs\": [")

      val baselineMap = baseline.scenarioResults.associateBy { it.scenarioName }
      current.scenarioResults.forEachIndexed { index, currentScenario ->
        val baselineScenario = baselineMap[currentScenario.scenarioName]
        if (baselineScenario != null) {
          val incDiff = percentChange(baselineScenario.incrementalMetrics.meanMs, currentScenario.incrementalMetrics.meanMs)
          val nonIncDiff = percentChange(baselineScenario.nonIncrementalMetrics.meanMs, currentScenario.nonIncrementalMetrics.meanMs)

          appendLine("    {")
          appendLine("      \"scenario\": \"${currentScenario.scenarioName}\",")
          appendLine("      \"incremental_change_percent\": ${formatDouble(incDiff)},")
          appendLine("      \"non_incremental_change_percent\": ${formatDouble(nonIncDiff)},")
          appendLine("      \"baseline_speedup\": ${formatDouble(baselineScenario.speedup)},")
          appendLine("      \"current_speedup\": ${formatDouble(currentScenario.speedup)}")
          if (index < current.scenarioResults.size - 1) {
            appendLine("    },")
          } else {
            appendLine("    }")
          }
        }
      }

      appendLine("  ]")
      appendLine("}")
    }
  }

  private fun percentChange(baseline: Long, current: Long): Double {
    if (baseline == 0L) return 0.0
    return ((current - baseline).toDouble() / baseline) * 100
  }
}
