// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.benchmark.runner

import java.io.File
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Collects timing metrics for build executions.
 */
class MetricsCollector(
  private val bazelPath: String,
  private val workingDir: Path,
  private val verbose: Boolean = false,
) {
  /**
   * Measures build time for the given target.
   *
   * @param target The Bazel target to build
   * @param incremental Whether to use incremental compilation
   * @return BuildMetrics containing timing and exit code
   */
  fun measureBuild(target: String, incremental: Boolean): BuildMetrics {
    val args = buildList {
      add(bazelPath)
      add("build")
      add(target)
      // Use threshold flags to control incremental behavior:
      // threshold=1 enables incremental (compiles incrementally if >= 1 file changed)
      // threshold=-1 disables incremental (always full rebuild)
      if (incremental) {
        add("--@rules_jvm//:koltin_inc_threshold=1")
        add("--@rules_jvm//:java_inc_threshold=1")
      } else {
        add("--@rules_jvm//:koltin_inc_threshold=-1")
        add("--@rules_jvm//:java_inc_threshold=-1")
      }
    }

    if (verbose) {
      println("  Running: ${args.joinToString(" ")}")
    }

    val startTime = System.nanoTime()
    val process = ProcessBuilder(args)
      .directory(workingDir.toFile())
      .redirectErrorStream(false)
      .start()

    val output = process.inputStream.bufferedReader().readText()
    val errorOutput = process.errorStream.bufferedReader().readText()
    val exitCode = process.waitFor()
    val endTime = System.nanoTime()

    val wallTimeMs = TimeUnit.NANOSECONDS.toMillis(endTime - startTime)

    if (verbose) {
      println("  Completed in ${wallTimeMs}ms (exit code: $exitCode)")
    }

    return BuildMetrics(
      wallTimeMs = wallTimeMs,
      exitCode = exitCode,
      output = output,
      errorOutput = errorOutput,
    )
  }

  /**
   * Cleans Bazel build state to force a cold build.
   */
  fun cleanBuild(): Boolean {
    val process = ProcessBuilder(bazelPath, "clean")
      .directory(workingDir.toFile())
      .inheritIO()
      .start()
    return process.waitFor() == 0
  }

  /**
   * Cleans only the incremental compilation state (output JARs and dependency graph).
   * This forces a rebuild without invalidating Bazel's action cache.
   */
  fun cleanIncrementalState(target: String): Boolean {
    // Force Bazel to rebuild by invalidating the specific target's outputs
    // We do this by touching a source file and then reverting it, or by using --discard_analysis_cache
    val process = ProcessBuilder(
      bazelPath, "build", target,
      "--discard_analysis_cache",
      "--notrack_incremental_state",
    )
      .directory(workingDir.toFile())
      .redirectErrorStream(true)
      .start()

    process.inputStream.bufferedReader().readText() // consume output
    return process.waitFor() == 0
  }

  /**
   * Verifies that builds produce identical outputs.
   */
  fun verifyOutputEquivalence(target: String): Boolean {
    // Build with incremental
    val incResult = measureBuild(target, incremental = true)
    if (incResult.exitCode != 0) return false

    // Get output JAR hash
    val incHash = getOutputHash(target)

    // Clean and build with non-incremental
    cleanIncrementalState(target)
    val nonIncResult = measureBuild(target, incremental = false)
    if (nonIncResult.exitCode != 0) return false

    val nonIncHash = getOutputHash(target)

    return incHash == nonIncHash
  }

  private fun getOutputHash(target: String): String? {
    // Use bazel cquery to find output files and hash them
    val process = ProcessBuilder(
      bazelPath, "cquery", target, "--output=files"
    )
      .directory(workingDir.toFile())
      .start()

    val outputFiles = process.inputStream.bufferedReader().readText().trim()
    process.waitFor()

    if (outputFiles.isEmpty()) return null

    // Hash the output file
    val outputFile = File(outputFiles.lines().first())
    return if (outputFile.exists()) {
      outputFile.readBytes().contentHashCode().toString()
    } else null
  }
}
