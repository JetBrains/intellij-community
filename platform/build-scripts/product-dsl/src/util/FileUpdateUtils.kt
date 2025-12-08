// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.util

import org.jetbrains.intellij.build.productLayout.stats.FileChangeStatus
import java.nio.file.Files
import java.nio.file.Path

/**
 * Utilities for atomic file updates with change detection.
 * Provides consistent file update logic across all generators.
 */
internal object FileUpdateUtils {
  /**
   * Updates a file if its content has changed.
   * Creates parent directories if needed.
   *
   * @param path The file path to update
   * @param newContent The new content to write
   * @return Status indicating whether the file was created, modified, or unchanged
   */
  fun updateIfChanged(path: Path, newContent: String): FileChangeStatus {
    return when {
      !Files.exists(path) -> {
        Files.createDirectories(path.parent)
        Files.writeString(path, newContent)
        FileChangeStatus.CREATED
      }
      Files.readString(path) == newContent -> FileChangeStatus.UNCHANGED
      else -> {
        Files.writeString(path, newContent)
        FileChangeStatus.MODIFIED
      }
    }
  }

  /**
   * Compares old and new content and writes if changed.
   * Use when you already have the old content loaded.
   *
   * @param path The file path to update
   * @param oldContent The current content of the file
   * @param newContent The new content to write
   * @return Status indicating whether the file was modified or unchanged
   */
  fun writeIfChanged(path: Path, oldContent: String, newContent: String): FileChangeStatus {
    return if (newContent == oldContent) {
      FileChangeStatus.UNCHANGED
    }
    else {
      Files.writeString(path, newContent)
      FileChangeStatus.MODIFIED
    }
  }
}
