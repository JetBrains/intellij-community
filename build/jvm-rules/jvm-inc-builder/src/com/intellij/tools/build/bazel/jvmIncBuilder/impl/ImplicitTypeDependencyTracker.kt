// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder.impl

import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks source files that contain declarations with compiler-inferred types
 * that depend on definitions from other files.
 *
 * This information is used for incremental compilation to ensure that files
 * with inferred types are recompiled when their type dependencies change.
 *
 * Only public/protected API declarations are tracked, as they could affect
 * dependent modules. Private, internal, and local declarations are skipped.
 */
class ImplicitTypeDependencyTracker {
  private val filesWithInferredExternalTypes: MutableSet<String> = ConcurrentHashMap.newKeySet()

  /**
   * Records that a source file has a declaration with an inferred type
   * that depends on a definition declared in another file.
   *
   * @param sourceFilePath Path to the source file containing the declaration with inferred type
   */
  fun recordFileWithInferredExternalType(sourceFilePath: String) {
    filesWithInferredExternalTypes.add(sourceFilePath)
  }

  /**
   * Checks if a file has already been recorded as having inferred external type dependencies.
   *
   * @param sourceFilePath Path to the source file to check
   * @return true if the file has already been recorded
   */
  fun isFileRecorded(sourceFilePath: String): Boolean = sourceFilePath in filesWithInferredExternalTypes

  /**
   * Returns the set of source files that have inferred external type dependencies.
   *
   * @return Set of source file paths
   */
  fun getAffectedFiles(): Set<String> = filesWithInferredExternalTypes

  /**
   * Clears all tracked data. Should be called at the start of each compilation session.
   */
  fun clear() {
    filesWithInferredExternalTypes.clear()
  }
}
