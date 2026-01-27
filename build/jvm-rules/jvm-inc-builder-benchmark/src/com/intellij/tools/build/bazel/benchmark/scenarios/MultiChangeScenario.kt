// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.benchmark.scenarios

import java.nio.file.Path

/**
 * Scenario that tests compilation after multiple file changes.
 *
 * Modifies a percentage of files across the project to simulate
 * a larger changeset (like a merge or refactoring).
 *
 * Expected behavior: Incremental < Non-incremental, speedup varies by dependency graph
 */
class MultiChangeScenario(
  projectPath: Path,
  private val changePercent: Int = 10,
) : BaseScenario(projectPath) {

  override val name: String = "multi_change"

  override val description: String = "Modify $changePercent% of files"

  private val modifiedFiles = mutableMapOf<Path, String>()

  override fun setup() {
    // Find all source files
    val allFiles = findSourceFiles(".*\\.(kt|java)")
    if (allFiles.isEmpty()) {
      println("Warning: No source files found in project")
      return
    }

    // Select files to modify
    val filesToModify = allFiles
      .shuffled()
      .take((allFiles.size * changePercent) / 100)
      .coerceAtLeast(1)

    // Modify each file
    filesToModify.forEach { file ->
      val originalContent = file.toFile().readText()
      modifiedFiles[file] = originalContent

      val modifiedContent = addChange(file, originalContent)
      file.toFile().writeText(modifiedContent)
    }
  }

  override fun cleanup() {
    // Restore all modified files
    modifiedFiles.forEach { (file, content) ->
      file.toFile().writeText(content)
    }
    modifiedFiles.clear()
  }

  override fun expectedBehavior(): String =
    "Incremental < Non-incremental (varies by dependency graph)"

  private fun addChange(file: Path, content: String): String {
    val isKotlin = file.toString().endsWith(".kt")
    val timestamp = System.currentTimeMillis()
    val randomSuffix = (Math.random() * 10000).toInt()

    return if (isKotlin) {
      val lastBrace = content.lastIndexOf('}')
      if (lastBrace > 0) {
        content.substring(0, lastBrace) +
          "\n  private fun benchmarkMulti${timestamp}_$randomSuffix() {}\n" +
          content.substring(lastBrace)
      } else content
    } else {
      val lastBrace = content.lastIndexOf('}')
      if (lastBrace > 0) {
        content.substring(0, lastBrace) +
          "\n  private void benchmarkMulti${timestamp}_$randomSuffix() {}\n" +
          content.substring(lastBrace)
      } else content
    }
  }

  private fun <T> List<T>.coerceAtLeast(minSize: Int): List<T> {
    return if (size >= minSize) this else this.take(minSize.coerceAtMost(size))
  }
}
