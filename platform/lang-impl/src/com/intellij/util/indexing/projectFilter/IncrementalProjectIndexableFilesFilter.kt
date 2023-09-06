// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.projectFilter

import com.intellij.util.containers.ConcurrentBitSet
import com.intellij.util.indexing.IdFilter

internal class IncrementalProjectIndexableFilesFilter : IdFilter() {
  private val fileIds: ConcurrentBitSet = ConcurrentBitSet.create()

  override fun getFilteringScopeType(): FilterScopeType = FilterScopeType.PROJECT_AND_LIBRARIES

  override fun containsFileId(fileId: Int): Boolean = fileIds.get(fileId)

  @Suppress("LocalVariableName")
  fun ensureFileIdPresent(fileId: Int, add: () -> Boolean): FileAddStatus {
    assert(fileId > 0)

    val _fileIds = fileIds
    if (_fileIds.get(fileId)) {
      return FileAddStatus.PRESENT
    }

    if (add()) {
      _fileIds.set(fileId)
      return FileAddStatus.PRESENT
    }
    return FileAddStatus.SKIPPED
  }

  fun removeFileId(fileId: Int) {
    assert(fileId > 0)
    fileIds.clear(fileId)
  }
}