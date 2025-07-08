// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("NonIndexableFilesUtils")

package com.intellij.util.indexing

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.containers.ArrayListSet
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.SmartHashSet
import com.intellij.util.containers.prefixTree.set.toMutablePrefixTreeSet
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexEx
import org.jetbrains.annotations.ApiStatus
import java.util.Collections

@ApiStatus.Internal
@RequiresBackgroundThread
@RequiresReadLock
fun WorkspaceFileIndexEx.contentUnindexedRoots(): Set<VirtualFile> {
  val roots = mutableSetOf<VirtualFile>()
  visitFileSets { fileSet, _ ->
    val root = fileSet.root
    if (fileSet.kind == WorkspaceFileKind.CONTENT_NON_INDEXABLE && !isIndexable(root)) {
      roots.add(fileSet.root)
    }
  }
  return roots
}

internal fun iterateNonIndexableFilesImpl(project: Project, inputFilter: VirtualFileFilter?, processor: ContentIterator): Boolean {
  val workspaceFileIndex = WorkspaceFileIndexEx.getInstance(project)
  val roots: Set<VirtualFile> = ReadAction.nonBlocking<Set<VirtualFile>> { workspaceFileIndex.contentUnindexedRoots() }.executeSynchronously()
  return workspaceFileIndex.iterateNonIndexableFilesImpl(roots, inputFilter ?: VirtualFileFilter.ALL, processor)
}


private fun WorkspaceFileIndex.allFileSets(root: VirtualFile) = runReadAction { findFileSets(root, true, true, true, true, true, true) }

@RequiresBackgroundThread
private fun WorkspaceFileIndex.iterateNonIndexableFilesImpl(roots: Set<VirtualFile>, filter: VirtualFileFilter, processor: ContentIterator): Boolean {
  for (root in roots) {
    val rootFileSets = allFileSets(root = root).toSet()
    val res = VfsUtilCore.visitChildrenRecursively(root, object : VirtualFileVisitor<Any?>() {
      override fun visitFileEx(file: VirtualFile): Result {
        ProgressManager.checkCanceled()
        val currentFileSets = allFileSets(root = file)
        return when {
          currentFileSets.size != rootFileSets.size -> SKIP_CHILDREN
          !rootFileSets.containsAll(currentFileSets) -> SKIP_CHILDREN
          !filter.accept(file) -> SKIP_CHILDREN
          !processor.processFile(file) -> skipTo(root) // terminate processing
          else -> CONTINUE
        }
      }
    })
    if (res.skipChildren && res.skipToParent == root) return false
  }
  return true
}
