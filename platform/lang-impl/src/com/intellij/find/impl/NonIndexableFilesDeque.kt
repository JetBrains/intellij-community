// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl

import com.intellij.find.impl.FindInProjectUtil.FIND_IN_FILES_SEARCH_IN_NON_INDEXABLE
import com.intellij.ide.util.gotoByName.contentUnindexedRoots
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexEx
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Internal
interface FilesDequeue {
  @RequiresReadLock
  fun computeNext(): VirtualFile?
}

private class NonIndexableFilesDequeueImpl (private val roots: Set<VirtualFile>, private val filter: (VirtualFile) -> Boolean) : FilesDequeue {
  private val bfsQueue: ArrayDeque<VirtualFile> =  ArrayDeque(roots)
  private val visitedRoots: MutableSet<VirtualFile> = mutableSetOf()

  @RequiresReadLock
  override fun computeNext(): VirtualFile? {
    while (bfsQueue.isNotEmpty()) {
      val file = bfsQueue.removeFirst()

      if (file in visitedRoots) continue
      if (file in roots) visitedRoots.add(file)

      if (!filter(file)) continue

      if (file.isDirectory) bfsQueue.addAll(file.children)
      return file
    }
    return null
  }
}

private object EmptyFilesDequeue : FilesDequeue {
  override fun computeNext(): VirtualFile? = null
}


@ApiStatus.Internal
@RequiresReadLock
fun nonIndexableFiles(project: Project): FilesDequeue {
  if (project.getUserData(FIND_IN_FILES_SEARCH_IN_NON_INDEXABLE) != true){
    return EmptyFilesDequeue
  }

  val workspaceFileIndex = WorkspaceFileIndex.getInstance(project) as WorkspaceFileIndexEx
  return NonIndexableFilesDequeueImpl(workspaceFileIndex.contentUnindexedRoots()) { file ->
    !file.isIndexedOrExcluded(workspaceFileIndex)
  }
}


private fun VirtualFile.isIndexedOrExcluded(workspaceFileIndex: WorkspaceFileIndexEx): Boolean {
  return workspaceFileIndex.isIndexable(this) || !workspaceFileIndex.isInWorkspace(this)
}
