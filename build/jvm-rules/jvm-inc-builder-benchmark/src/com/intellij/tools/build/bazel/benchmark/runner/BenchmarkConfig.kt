// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.benchmark.runner

import com.intellij.tools.build.bazel.benchmark.scenarios.Scenario
import java.nio.file.Path

/**
 * Configuration for benchmark execution.
 */
data class BenchmarkConfig(
  val projectPath: Path,
  val target: String,
  val scenarios: List<Scenario>,
  val warmupIterations: Int = 3,
  val measurementIterations: Int = 5,
  val bazelPath: String = "bazel",
  val outputPath: Path? = null,
  val verbose: Boolean = false,
)

/**
 * Configuration for generating synthetic test projects.
 */
data class ProjectConfig(
  val name: String,
  val language: Language,
  val totalFiles: Int,
  val apiClassPercent: Int = 20,
  val implClassPercent: Int = 50,
  val utilClassPercent: Int = 30,
  val avgDepsPerClass: Int = 3,
) {
  init {
    require(apiClassPercent + implClassPercent + utilClassPercent == 100) {
      "Class percentages must sum to 100"
    }
    require(totalFiles > 0) { "Total files must be positive" }
  }

  val apiClassCount: Int get() = (totalFiles * apiClassPercent) / 100
  val implClassCount: Int get() = (totalFiles * implClassPercent) / 100
  val utilClassCount: Int get() = totalFiles - apiClassCount - implClassCount
}

/**
 * Supported language configurations for benchmark projects.
 */
enum class Language(val extension: String) {
  JAVA("java"),
  KOTLIN("kt"),
  MIXED("mixed");

  fun getExtensionFor(index: Int): String = when (this) {
    JAVA -> "java"
    KOTLIN -> "kt"
    MIXED -> if (index % 2 == 0) "kt" else "java"
  }
}
