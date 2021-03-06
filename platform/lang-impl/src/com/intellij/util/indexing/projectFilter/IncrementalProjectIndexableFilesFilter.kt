// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.projectFilter

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.util.containers.ConcurrentBitSet
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexImpl
import com.intellij.util.indexing.IdFilter
import com.intellij.util.indexing.UnindexedFilesUpdater

internal class IncrementalProjectIndexableFilesFilter(private val project: Project): IdFilter() {
  private val fileBasedIndex = FileBasedIndex.getInstance() as FileBasedIndexImpl
  @Volatile
  private var memorySnapshot: ConcurrentBitSet? = null

  override fun containsFileId(fileId: Int): Boolean {
    var snapshot = memorySnapshot
    if (snapshot == null) {
      if (skipFilterRestoration()) {
        return true
      }
      snapshot = calculateIdsByProject()
      memorySnapshot = snapshot
    }
    return snapshot.get(fileId)
  }

  private fun skipFilterRestoration(): Boolean {
    return UnindexedFilesUpdater.isIndexUpdateInProgress(project)
  }

  private fun calculateIdsByProject(): ConcurrentBitSet {
    val result = ConcurrentBitSet.create()
    fileBasedIndex.iterateIndexableFiles(ContentIterator {
      if (it is VirtualFileWithId) {
        result.set(it.id)
      }
      return@ContentIterator true
    }, project, null)
    return result
  }

  fun ensureFileIdPresent(fileId: Int, add: () -> Boolean) {
    assert(fileId > 0)

    val snapshot = memorySnapshot
    if (snapshot != null && snapshot.get(fileId)) {
      return
    }

    if (add()) {
      snapshot?.set(fileId)
    }
  }

  fun removeFileId(fileId: Int) {
    assert(fileId > 0)

    memorySnapshot?.clear(fileId)
  }

  fun drop() {
    memorySnapshot = null
  }
}