// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout

import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestWatcher
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * JUnit 5 extension that logs test failures to a file for AI-readable debugging.
 *
 * Useful when test output is truncated by tools like MCP, which only show exit codes.
 *
 * Usage: Add `@ExtendWith(TestFailureLogger::class)` to test classes.
 *
 * Enabled by setting `TEST_FAILURE_LOG` env var to a directory path.
 * Output: `{TEST_FAILURE_LOG}/product-dsl-test-failures.log`
 *
 * The log file is cleared at the start of each test run.
 */
class TestFailureLogger : TestWatcher {
  companion object {
    @Volatile
    private var clearedInThisRun = false
  }

  override fun testFailed(context: ExtensionContext, cause: Throwable) {
    val logDir = System.getenv("TEST_FAILURE_LOG") ?: return
    val logFile = Path.of(logDir).resolve("product-dsl-test-failures.log")

    // Clear file on first write in this JVM session
    val options = if (!clearedInThisRun) {
      clearedInThisRun = true
      arrayOf(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
    }
    else {
      arrayOf(StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    }

    val content = buildString {
      appendLine("===== FAILED: ${context.displayName} =====")
      appendLine("Class: ${context.testClass.map { it.name }.orElse("unknown")}")
      appendLine("Method: ${context.testMethod.map { it.name }.orElse("unknown")}")
      appendLine()
      appendLine("Message: ${cause.message}")
      appendLine()
      appendLine("Stack trace:")
      appendLine(cause.stackTraceToString())
      appendLine()
      appendLine("=".repeat(60))
      appendLine()
    }

    Files.writeString(logFile, content, *options)
  }

  override fun testSuccessful(context: ExtensionContext) {
    // Clear file on first test (even if successful) so empty file = all passed
    if (!clearedInThisRun) {
      val logDir = System.getenv("TEST_FAILURE_LOG") ?: return
      val logFile = Path.of(logDir).resolve("product-dsl-test-failures.log")
      Files.writeString(logFile, "", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
      clearedInThisRun = true
    }
  }
}
