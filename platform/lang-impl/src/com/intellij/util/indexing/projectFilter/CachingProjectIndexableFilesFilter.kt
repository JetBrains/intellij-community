// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.projectFilter

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.ManagingFS
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
      val file = ManagingFS.getInstance().findFileById(fileId)
      if (file == null) {
        return false
      }
      val isIndexable = IndexableFilesIndex.getInstance(project).shouldBeIndexed(file)
      if (_fileIds.compareAndSet(fileId, null, isIndexable)) {
        return isIndexable
      }
    }
  }

  @Suppress("LocalVariableName")
  override fun ensureFileIdPresent(fileId: Int, add: () -> Boolean): Boolean {
    assert(fileId > 0)

    val _fileIds = fileIds
    if (_fileIds[fileId] == true) {
      return true
    }

    if (add()) {
      _fileIds[fileId] = true
      return true
    }
    return containsFileId(_fileIds, fileId)
  }

  override fun removeFileId(fileId: Int) {
    assert(fileId > 0)
    fileIds[fileId] = false
  }

  override fun resetFileIds() {
    fileIds.clear()
  }

  override fun runHealthCheck(project: Project): List<HealthCheckError> {
    return runAndCheckThatNoChangesHappened {
      val errors = mutableListOf<HealthCheckError>()
      for (fileId in 0 until fileIds.size()) {
        if (fileIds[fileId] == true) {
          val file = ManagingFS.getInstance().findFileById(fileId)
          if (file == null) {
            errors.add(NotIndexableFileIsInFilterError(project, null, fileId, this))
          }
          else if (!IndexableFilesIndex.getInstance(project).shouldBeIndexed(file)) {
            errors.add(NotIndexableFileIsInFilterError(project, file, fileId, this))
          }
        }
      }
      errors
    }
  }

  class NotIndexableFileIsInFilterError(private val project: Project,
                                        private val virtualFile: VirtualFile?,
                                        private val fileId: Int,
                                        private val filter: ProjectIndexableFilesFilter) : HealthCheckError {
    override val presentableText: String
      get() = "file name=${virtualFile?.path} id=$fileId is found in filter even though it's not indexable (${project.name})"

    override fun fix() {
      filter.removeFileId(fileId)
    }
  }
}