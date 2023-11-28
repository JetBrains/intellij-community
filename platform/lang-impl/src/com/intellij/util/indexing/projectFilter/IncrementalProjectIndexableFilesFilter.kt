// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.projectFilter

import com.intellij.util.containers.ConcurrentBitSet

internal class IncrementalProjectIndexableFilesFilter : ProjectIndexableFilesFilter() {
  private val fileIds: ConcurrentBitSet = ConcurrentBitSet.create()

  override fun getFilteringScopeType(): FilterScopeType = FilterScopeType.PROJECT_AND_LIBRARIES

  override fun containsFileId(fileId: Int): Boolean = fileIds.get(fileId)

  @Suppress("LocalVariableName")
  override fun ensureFileIdPresent(fileId: Int, add: () -> Boolean): Boolean {
    assert(fileId > 0)

    return runUpdate {
      val _fileIds = fileIds
      if (_fileIds.get(fileId)) {
        true
      }
      else if (add()) {
        _fileIds.set(fileId)
        true
      }
      else false
    }
  }

  override fun removeFileId(fileId: Int) {
    assert(fileId > 0)
    runUpdate {
      fileIds.clear(fileId)
    }
  }

  override fun resetFileIds() {
    fileIds.clear()
  }
}