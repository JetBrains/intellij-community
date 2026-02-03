// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.projectFilter

import com.intellij.openapi.project.Project

internal class IncrementalProjectIndexableFilesFilterFactory : ProjectIndexableFilesFilterFactory() {
  override fun create(project: Project, currentVfsCreationTimestamp: Long): ProjectIndexableFilesFilter {
    return IncrementalProjectIndexableFilesFilter()
  }
}

internal open class IncrementalProjectIndexableFilesFilter(protected val fileIds: ConcurrentFileIds = ConcurrentFileIds())
  : ProjectIndexableFilesFilter(true) {

  override fun containsFileId(fileId: Int): Boolean = fileIds[fileId]

  @Suppress("LocalVariableName")
  override fun ensureFileIdPresent(fileId: Int, add: () -> Boolean): Boolean {
    assert(fileId > 0)

    return runUpdate {
      val _fileIds = fileIds
      if (_fileIds[fileId]) {
        true
      }
      else if (add()) {
        _fileIds[fileId] = true
        true
      }
      else false
    }
  }

  override fun removeFileId(fileId: Int) {
    assert(fileId > 0)
    runUpdate {
      fileIds[fileId] = false
    }
  }

  override fun resetFileIds() {
    fileIds.clear()
  }

  override fun getFileStatuses(): Sequence<Pair<Int, Boolean>> {
    return (0 until fileIds.size).asSequence().map { it to fileIds[it] }
  }
}