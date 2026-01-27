// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.benchmark.runner

import java.time.Instant

/**
 * Complete benchmark result containing all scenario measurements.
 */
data class BenchmarkResult(
  val timestamp: Instant,
  val config: BenchmarkConfig,
  val projectInfo: ProjectInfo,
  val scenarioResults: List<ScenarioResult>,
)

/**
 * Information about the benchmarked project.
 */
data class ProjectInfo(
  val name: String,
  val fileCount: Int,
  val language: Language,
)

/**
 * Results for a single benchmark scenario.
 */
data class ScenarioResult(
  val scenarioName: String,
  val incrementalMetrics: AggregatedMetrics,
  val nonIncrementalMetrics: AggregatedMetrics,
) {
  val speedup: Double
    get() = if (incrementalMetrics.meanMs > 0) {
      nonIncrementalMetrics.meanMs.toDouble() / incrementalMetrics.meanMs
    } else {
      0.0
    }
}

/**
 * Aggregated metrics from multiple measurement iterations.
 */
data class AggregatedMetrics(
  val measurements: List<BuildMetrics>,
) {
  val meanMs: Long
    get() = if (measurements.isEmpty()) 0 else measurements.map { it.wallTimeMs }.average().toLong()

  val medianMs: Long
    get() = if (measurements.isEmpty()) 0 else measurements.map { it.wallTimeMs }.sorted()
      .let { sorted -> sorted[sorted.size / 2] }

  val stdDevMs: Long
    get() {
      if (measurements.size < 2) return 0
      val mean = meanMs.toDouble()
      val variance = measurements.map { (it.wallTimeMs - mean).let { d -> d * d } }.average()
      return kotlin.math.sqrt(variance).toLong()
    }

  val minMs: Long
    get() = measurements.minOfOrNull { it.wallTimeMs } ?: 0

  val maxMs: Long
    get() = measurements.maxOfOrNull { it.wallTimeMs } ?: 0

  val successRate: Double
    get() = if (measurements.isEmpty()) 0.0 else {
      measurements.count { it.exitCode == 0 }.toDouble() / measurements.size
    }
}

/**
 * Metrics from a single build execution.
 */
data class BuildMetrics(
  val wallTimeMs: Long,
  val exitCode: Int,
  val output: String = "",
  val errorOutput: String = "",
)

/**
 * Single measurement containing both incremental and non-incremental results.
 */
data class Measurement(
  val incremental: BuildMetrics,
  val nonIncremental: BuildMetrics,
)
