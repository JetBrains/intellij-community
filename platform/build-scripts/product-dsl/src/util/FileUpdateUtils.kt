// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.util

import org.jetbrains.intellij.build.productLayout.stats.FileChangeStatus
import org.jetbrains.intellij.build.productLayout.validation.FileChangeType
import org.jetbrains.intellij.build.productLayout.validation.FileDiff
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Strategy for file update operations.
 * Implementations determine whether to actually write files or record diffs for validation.
 */
sealed interface FileUpdateStrategy {
  /**
   * Updates a file if content has changed, or records diff in dry run mode.
   * @return Status indicating whether file was created, modified, or unchanged
   */
  fun updateIfChanged(path: Path, newContent: String): FileChangeStatus

  /**
   * Writes file if content changed, or records diff in dry run mode.
   * @return Status indicating whether file was modified or unchanged
   */
  fun writeIfChanged(path: Path, oldContent: String, newContent: String): FileChangeStatus

  /**
   * Deletes a file or records deletion diff in dry run mode.
   */
  fun delete(path: Path)

  /**
   * Returns diffs collected during operations.
   */
  fun getDiffs(): List<FileDiff>
}

/**
 * Deferred file writer - collects changes during generation,
 * commits only when explicitly requested.
 * Use when you want to validate before writing.
 */
internal class DeferredFileUpdater(private val projectRoot: Path) : FileUpdateStrategy {
  private val _diffs = CopyOnWriteArrayList<FileDiff>()

  override fun updateIfChanged(path: Path, newContent: String): FileChangeStatus {
    val oldContent = if (Files.exists(path)) Files.readString(path) else ""
    return writeIfChanged(path, oldContent, newContent)
  }

  override fun writeIfChanged(path: Path, oldContent: String, newContent: String): FileChangeStatus {
    if (newContent == oldContent) {
      return FileChangeStatus.UNCHANGED
    }

    val changeType = if (oldContent.isEmpty()) FileChangeType.CREATE else FileChangeType.MODIFY
    val relativePath = projectRoot.relativize(path)
    val context = "Generated file is out of sync: $relativePath\nRun 'Generate Product Layouts' or 'bazel run //platform/buildScripts:plugin-model-tool' to update."
    _diffs.add(FileDiff(context = context, path = path, expectedContent = newContent, actualContent = oldContent, changeType = changeType))
    return if (oldContent.isEmpty()) FileChangeStatus.CREATED else FileChangeStatus.MODIFIED
  }

  override fun delete(path: Path) {
    val existingContent = if (Files.exists(path)) Files.readString(path) else ""
    val relativePath = projectRoot.relativize(path)
    val context = "File should be deleted: $relativePath\nRun 'Generate Product Layouts' or 'bazel run //platform/buildScripts:plugin-model-tool' to update."
    _diffs.add(FileDiff(
      context = context,
      path = path,
      expectedContent = "",
      actualContent = existingContent,
      changeType = FileChangeType.DELETE,
    ))
  }

  override fun getDiffs(): List<FileDiff> = _diffs.toList()

  /**
   * Commits all collected changes to disk.
   * Call only after validation passes.
   */
  fun commit() {
    for (diff in _diffs) {
      when (diff.changeType) {
        FileChangeType.CREATE -> {
          Files.createDirectories(diff.path.parent)
          Files.writeString(diff.path, diff.expectedContent)
        }
        FileChangeType.MODIFY -> {
          Files.writeString(diff.path, diff.expectedContent)
        }
        FileChangeType.DELETE -> {
          Files.deleteIfExists(diff.path)
        }
      }
    }
  }
}
