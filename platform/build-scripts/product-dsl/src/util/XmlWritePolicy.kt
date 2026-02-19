// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.util

import org.jetbrains.intellij.build.productLayout.model.error.FileDiff
import org.jetbrains.intellij.build.productLayout.pipeline.GenerationMode
import org.jetbrains.intellij.build.productLayout.stats.FileChangeStatus
import java.nio.file.Path

/**
 * Global write policy for XML artifacts.
 *
 * Decides whether XML updates are written, diffed, or skipped based on [GenerationMode].
 *
 * - NORMAL: allow updates (diffs recorded via [FileUpdateStrategy])
 * - VALIDATE_ONLY: diff-only (no commit even if commitChanges is true)
 * - UPDATE_SUPPRESSIONS: no XML writes and no diffs
 */
internal class XmlWritePolicy(
  private val mode: GenerationMode,
  private val delegate: FileUpdateStrategy,
) : FileUpdateStrategy {
  override fun updateIfChanged(path: Path, newContent: String): FileChangeStatus {
    return when (mode) {
      GenerationMode.UPDATE_SUPPRESSIONS -> FileChangeStatus.UNCHANGED
      GenerationMode.NORMAL, GenerationMode.VALIDATE_ONLY -> delegate.updateIfChanged(path, newContent)
    }
  }

  override fun writeIfChanged(path: Path, oldContent: String, newContent: String): FileChangeStatus {
    return when (mode) {
      GenerationMode.UPDATE_SUPPRESSIONS -> FileChangeStatus.UNCHANGED
      GenerationMode.NORMAL, GenerationMode.VALIDATE_ONLY -> delegate.writeIfChanged(path, oldContent, newContent)
    }
  }

  override fun delete(path: Path) {
    if (mode != GenerationMode.UPDATE_SUPPRESSIONS) {
      delegate.delete(path)
    }
  }

  override fun getDiffs(): List<FileDiff> {
    return if (mode == GenerationMode.UPDATE_SUPPRESSIONS) emptyList() else delegate.getDiffs()
  }
}
