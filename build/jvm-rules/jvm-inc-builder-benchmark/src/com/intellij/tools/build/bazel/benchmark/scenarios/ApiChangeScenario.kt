// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.benchmark.scenarios

import java.nio.file.Path

/**
 * Scenario that tests compilation after an API change.
 *
 * Adds a new public method to an API class, which should trigger
 * recompilation of the API class and potentially its dependents
 * (though adding a method typically doesn't break existing code).
 *
 * Expected behavior: Incremental = API file + dependents that reference it,
 * Non-incremental = all files
 */
class ApiChangeScenario(
  projectPath: Path,
) : BaseScenario(projectPath) {

  override val name: String = "api_change"

  override val description: String = "Add public method to API class"

  private var targetFile: Path? = null
  private var originalContent: String? = null

  override fun setup() {
    // Find the first API file
    val apiFiles = getSourceFilesIn("api")
    if (apiFiles.isEmpty()) {
      println("Warning: No API files found in project")
      return
    }

    targetFile = apiFiles.first()
    originalContent = targetFile!!.toFile().readText()

    // Add a new public method
    val modifiedContent = addApiMethod(originalContent!!)
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
    "Incremental = API + dependents, Non-incremental = all files"

  private fun addApiMethod(content: String): String {
    val isKotlin = targetFile?.toString()?.endsWith(".kt") == true
    val timestamp = System.currentTimeMillis()

    return if (isKotlin) {
      // For Kotlin: add a public function before the closing brace
      val lastBrace = content.lastIndexOf('}')
      if (lastBrace > 0) {
        content.substring(0, lastBrace) +
          "\n  fun newApiMethod${timestamp}(): String = \"benchmark\"\n" +
          content.substring(lastBrace)
      } else content
    } else {
      // For Java: add a public method before the closing brace
      val lastBrace = content.lastIndexOf('}')
      if (lastBrace > 0) {
        content.substring(0, lastBrace) +
          "\n  public String newApiMethod${timestamp}() { return \"benchmark\"; }\n" +
          content.substring(lastBrace)
      } else content
    }
  }
}
