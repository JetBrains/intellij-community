// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.benchmark.scenarios

import java.nio.file.Path

/**
 * Interface for benchmark scenarios that test different compilation patterns.
 *
 * Each scenario sets up a specific state (e.g., modified files, clean state)
 * and then measures the build performance for both incremental and non-incremental modes.
 */
interface Scenario {
  /**
   * The name of this scenario for reporting purposes.
   */
  val name: String

  /**
   * A brief description of what this scenario tests.
   */
  val description: String

  /**
   * Sets up the scenario state before a build measurement.
   * This might modify source files, clear caches, etc.
   */
  fun setup()

  /**
   * Cleans up any changes made during setup.
   * Should restore the project to its original state.
   */
  fun cleanup()

  /**
   * Returns the expected behavior description for reporting.
   */
  fun expectedBehavior(): String
}

/**
 * Base class for scenarios that operate on a generated project.
 */
abstract class BaseScenario(
  protected val projectPath: Path,
) : Scenario {

  /**
   * Reads a source file's content.
   */
  protected fun readSourceFile(relativePath: String): String {
    return projectPath.resolve(relativePath).toFile().readText()
  }

  /**
   * Writes content to a source file.
   */
  protected fun writeSourceFile(relativePath: String, content: String) {
    projectPath.resolve(relativePath).toFile().writeText(content)
  }

  /**
   * Appends content to a source file.
   */
  protected fun appendToSourceFile(relativePath: String, content: String) {
    val file = projectPath.resolve(relativePath).toFile()
    file.appendText(content)
  }

  /**
   * Finds source files matching a pattern.
   */
  protected fun findSourceFiles(pattern: String): List<Path> {
    return projectPath.resolve("src").toFile()
      .walkTopDown()
      .filter { it.isFile && it.name.matches(Regex(pattern)) }
      .map { it.toPath() }
      .toList()
  }

  /**
   * Gets all source files in a directory.
   */
  protected fun getSourceFilesIn(dir: String): List<Path> {
    val directory = projectPath.resolve("src").resolve(dir).toFile()
    if (!directory.exists()) return emptyList()
    return directory.walkTopDown()
      .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
      .map { it.toPath() }
      .toList()
  }
}
