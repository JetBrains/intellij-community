// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.util

import org.jetbrains.intellij.build.productLayout.stats.FileChangeStatus
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Status indicating what type of change would occur in dry-run mode.
 */
enum class DryRunChangeStatus {
  /** File would be newly created */
  WOULD_CREATE,
  /** File content would be modified */
  WOULD_MODIFY,
}

/**
 * Represents a difference detected during dry-run mode.
 * Used to report files that would be modified if the generator were run.
 */
data class DryRunDiff(
  @JvmField val path: Path,
  @JvmField val expectedContent: String,
  @JvmField val actualContent: String,
  @JvmField val status: DryRunChangeStatus,
)

/**
 * Collector for dry-run mode diffs.
 * Pass through the call chain to record file changes without writing.
 */
class DryRunCollector {
  private val _diffs = CopyOnWriteArrayList<DryRunDiff>()

  val diffs: List<DryRunDiff> get() = _diffs

  fun record(diff: DryRunDiff) {
    _diffs.add(diff)
  }
}

/**
 * Collector for validation errors.
 * Pass through the call chain to collect errors instead of printing and exiting.
 */
class ValidationErrorCollector {
  private val _errors = CopyOnWriteArrayList<String>()

  val errors: List<String>
    get() = _errors

  internal fun record(error: String) {
    _errors.add(error)
  }
}

/**
 * Result of model generator validation.
 * Contains both file diffs (out-of-sync files) and validation errors (missing dependencies, etc.).
 */
data class ModelValidationResult(
  @JvmField val diffs: List<DryRunDiff>,
  @JvmField val errors: List<String>,
)

/**
 * Utilities for atomic file updates with change detection.
 * Provides consistent file update logic across all generators.
 */
internal object FileUpdateUtils {
  /**
   * Updates a file if its content has changed.
   * Creates parent directories if needed.
   * If collector is provided, records diffs instead of writing.
   *
   * @param path The file path to update
   * @param newContent The new content to write
   * @param dryRunCollector If non-null, records diffs instead of writing
   * @return Status indicating whether the file was created, modified, or unchanged
   */
  fun updateIfChanged(path: Path, newContent: String, dryRunCollector: DryRunCollector? = null): FileChangeStatus {
    if (Files.exists(path)) {
      return writeIfChanged(path = path, oldContent = Files.readString(path), newContent = newContent, dryRunCollector = dryRunCollector)
    }
    else {
      writeOrRecord(path = path, oldContent = "", newContent = newContent, dryRunCollector = dryRunCollector, status = DryRunChangeStatus.WOULD_CREATE, createDirs = true)
      return FileChangeStatus.CREATED
    }
  }

  /**
   * Compares old and new content and writes if changed.
   * Use when you already have the old content loaded.
   * If collector is provided, records diffs instead of writing.
   *
   * @param path The file path to update
   * @param oldContent The current content of the file
   * @param newContent The new content to write
   * @param dryRunCollector If non-null, records diffs instead of writing
   * @return Status indicating whether the file was modified or unchanged
   */
  fun writeIfChanged(path: Path, oldContent: String, newContent: String, dryRunCollector: DryRunCollector? = null): FileChangeStatus {
    if (newContent == oldContent) {
      return FileChangeStatus.UNCHANGED
    }
    else {
      writeOrRecord(path = path, oldContent = oldContent, newContent = newContent, dryRunCollector = dryRunCollector, status = DryRunChangeStatus.WOULD_MODIFY, createDirs = false)
      return FileChangeStatus.MODIFIED
    }
  }

  private fun writeOrRecord(path: Path, oldContent: String, newContent: String, dryRunCollector: DryRunCollector?, status: DryRunChangeStatus, createDirs: Boolean) {
    if (dryRunCollector != null) {
      dryRunCollector.record(DryRunDiff(path, expectedContent = oldContent, actualContent = newContent, status))
    }
    else {
      if (createDirs) {
        Files.createDirectories(path.parent)
      }
      Files.writeString(path, newContent)
    }
  }
}
