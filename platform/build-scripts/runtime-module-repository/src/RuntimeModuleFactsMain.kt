// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("RuntimeModuleFactsMain")

package com.intellij.platform.buildScripts.runtimeModuleRepository

import java.nio.file.Path

private const val PROJECT_DIR_OPTION = "--project-dir="
private const val OUTPUT_OPTION = "--output="

/**
 * Writes the [RuntimeModuleFactsTable] of every module of a JPS project.
 *
 * The options:
 * - `--project-dir=<dir>`: the JPS project model tree.
 * - `--output=<file>`: the facts file.
 */
fun main(args: Array<String>) {
  var projectDir: Path? = null
  var output: Path? = null
  for (arg in args) {
    when {
      arg.startsWith(PROJECT_DIR_OPTION) -> projectDir = Path.of(arg.removePrefix(PROJECT_DIR_OPTION))
      arg.startsWith(OUTPUT_OPTION) -> output = Path.of(arg.removePrefix(OUTPUT_OPTION))
      else -> throw IllegalArgumentException("Unknown argument '$arg'")
    }
  }
  requireNotNull(projectDir) { "${PROJECT_DIR_OPTION}<dir> is required" }
  requireNotNull(output) { "${OUTPUT_OPTION}<file> is required" }
  writeRuntimeModuleFacts(JpsRuntimeModuleModel(loadProject(projectDir)).toFactsTable(), output)
}
