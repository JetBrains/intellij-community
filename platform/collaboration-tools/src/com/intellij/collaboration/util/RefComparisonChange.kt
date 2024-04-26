// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.util

import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.history.ShortVcsRevisionNumber
import org.jetbrains.annotations.ApiStatus

/**
 * Descriptor of a file change between two revisions
 */
@ApiStatus.Experimental
data class RefComparisonChange(
  val revisionNumberBefore: ShortVcsRevisionNumber,
  val filePathBefore: FilePath?,
  val revisionNumberAfter: ShortVcsRevisionNumber,
  val filePathAfter: FilePath?,
) {
  companion object {
    val KEY: Key<RefComparisonChange> = Key.create("RefComparisonChange")
  }
}

val RefComparisonChange.fileStatus: FileStatus
  get() = when {
    filePathBefore == null -> FileStatus.ADDED
    filePathAfter == null -> FileStatus.DELETED
    else -> FileStatus.MODIFIED
  }

val RefComparisonChange.filePath: FilePath
  get() = (filePathAfter ?: filePathBefore)!!