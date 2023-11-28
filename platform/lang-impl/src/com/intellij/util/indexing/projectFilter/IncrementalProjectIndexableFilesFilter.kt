// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.projectFilter

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.util.containers.ConcurrentBitSet
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexImpl

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

  override fun runHealthCheck(project: Project): List<HealthCheckError> {
    return runAndCheckThatNoChangesHappened {
      val errors = mutableListOf<HealthCheckError>()
      val index = FileBasedIndex.getInstance() as FileBasedIndexImpl
      index.iterateIndexableFiles(ContentIterator {
        if (it is VirtualFileWithId) {
          val fileId = it.id
          if (!containsFileId(fileId)) {
            errors.add(MissingFileIdInFilterError(project, it, fileId, this))
          }
        }
        true
      }, project, ProgressManager.getInstance().progressIndicator)
      errors
    }
  }

  class MissingFileIdInFilterError(private val project: Project,
                                   private val virtualFile: VirtualFile,
                                   private val fileId: Int,
                                   private val filter: ProjectIndexableFilesFilter): HealthCheckError {
    override val presentableText: String
      get() = "file ${virtualFile.path} not found in filter of ${project.name}"

    override fun fix() {
      filter.ensureFileIdPresent(fileId) { true }
    }
  }
}