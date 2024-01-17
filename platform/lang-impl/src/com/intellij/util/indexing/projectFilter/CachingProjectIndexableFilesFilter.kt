// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.projectFilter

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.newvfs.ManagingFS
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess.VfsRootAccessNotAllowedError
import com.intellij.util.containers.ConcurrentThreeStateBitSet
import com.intellij.util.indexing.IndexableFilesIndex

internal class CachingProjectIndexableFilesFilter(private val project: Project) : ProjectIndexableFilesFilter() {
  private val fileIds: ConcurrentThreeStateBitSet = ConcurrentThreeStateBitSet.create()

  override fun getFilteringScopeType(): FilterScopeType = FilterScopeType.PROJECT_AND_LIBRARIES

  override fun containsFileId(fileId: Int): Boolean {
    return containsFileId(fileIds, fileId)
  }

  @Suppress("LocalVariableName")
  private fun containsFileId(_fileIds: ConcurrentThreeStateBitSet, fileId: Int): Boolean {
    while (true) {
      _fileIds[fileId]?.let { return it }
      try {
        val file = ManagingFS.getInstance().findFileById(fileId)
        val isIndexable = file == null || IndexableFilesIndex.getInstance(project).shouldBeIndexed(file)
        if (_fileIds.compareAndSet(fileId, null, isIndexable)) {
          return isIndexable
        }
      }
      catch (error: VfsRootAccessNotAllowedError) {
        return false
      }
    }
  }

  @Suppress("LocalVariableName")
  override fun ensureFileIdPresent(fileId: Int, add: () -> Boolean): Boolean {
    assert(fileId > 0)

    return runUpdate {
      val _fileIds = fileIds

      if (_fileIds[fileId] == true) {
        true
      }
      else if (add()) {
        _fileIds[fileId] = true
        true
      }
      else containsFileId(_fileIds, fileId)
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

  override fun runHealthCheck(project: Project): List<HealthCheckError> {
    return runAndCheckThatNoChangesHappened {
      val fileStatuses = (0 until fileIds.size()).asSequence().mapNotNull { fileId ->
        fileIds[fileId]?.let { status -> fileId to status }
      }
      runHealthCheck(project, false, fileStatuses)
    }
  }
}