// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.benchmark.scenarios

import java.nio.file.Path

/**
 * Scenario that tests compilation after an implementation change.
 *
 * Modifies a method body in an implementation class without changing
 * its public API. This should result in minimal recompilation for
 * incremental mode since no dependents are affected.
 *
 * Expected behavior: Incremental = recompile 1 file, Non-incremental = all files
 */
class ImplChangeScenario(
  projectPath: Path,
) : BaseScenario(projectPath) {

  override val name: String = "impl_change"

  override val description: String = "Single implementation file body change"

  private var targetFile: Path? = null
  private var originalContent: String? = null

  override fun setup() {
    // Find the first implementation file
    val implFiles = getSourceFilesIn("impl")
    if (implFiles.isEmpty()) {
      println("Warning: No implementation files found in project")
      return
    }

    targetFile = implFiles.first()
    originalContent = targetFile!!.toFile().readText()

    // Add a harmless implementation change (private method or comment modification)
    val modifiedContent = addImplementationChange(originalContent!!)
    targetFile!!.toFile().writeText(modifiedContent)
  }

  override fun cleanup() {
    // Restore original content
    targetFile?.let { file ->
      originalContent?.let { content ->
        file.toFile().writeText(content)
      }
    }
    targetFile = null
    originalContent = null
  }

  override fun expectedBehavior(): String =
    "Incremental = 1 file recompile, Non-incremental = all files"

  private fun addImplementationChange(content: String): String {
    // Add a private method or modify an existing implementation detail
    val isKotlin = targetFile?.toString()?.endsWith(".kt") == true

    return if (isKotlin) {
      // For Kotlin: add a private function before the closing brace
      val lastBrace = content.lastIndexOf('}')
      if (lastBrace > 0) {
        val timestamp = System.currentTimeMillis()
        content.substring(0, lastBrace) +
          "\n  private fun benchmarkChange${timestamp}() { /* benchmark modification */ }\n" +
          content.substring(lastBrace)
      } else content
    } else {
      // For Java: add a private method before the closing brace
      val lastBrace = content.lastIndexOf('}')
      if (lastBrace > 0) {
        val timestamp = System.currentTimeMillis()
        content.substring(0, lastBrace) +
          "\n  private void benchmarkChange${timestamp}() { /* benchmark modification */ }\n" +
          content.substring(lastBrace)
      } else content
    }
  }
}
