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
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSet
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetWithCustomData
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexEx
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@RequiresBackgroundThread
@RequiresReadLock
fun WorkspaceFileIndexEx.contentUnindexedRoots(): Set<VirtualFile> {
  val roots = mutableSetOf<VirtualFile>()
  visitFileSets { fileSet, _ ->
    val root = fileSet.root
    if (fileSet.kind == WorkspaceFileKind.CONTENT_NON_INDEXABLE && allFileSets(root, includeContentNonIndexableSets = false).recursive.isEmpty()) {
      roots.add(root)
    }
  }
  return roots
}

internal fun iterateNonIndexableFilesImpl(project: Project, inputFilter: VirtualFileFilter?, processor: ContentIterator): Boolean {
  val workspaceFileIndex = WorkspaceFileIndexEx.getInstance(project)
  val roots: Set<VirtualFile> = ReadAction.nonBlocking<Set<VirtualFile>> { workspaceFileIndex.contentUnindexedRoots() }.executeSynchronously()
  return workspaceFileIndex.iterateNonIndexableFilesImpl(roots, inputFilter ?: VirtualFileFilter.ALL, processor)
}

private data class AllFileSets(val recursive: List<WorkspaceFileSet>, val nonRecursive: List<WorkspaceFileSet>)

private fun WorkspaceFileIndex.allFileSets(root: VirtualFile, includeContentNonIndexableSets: Boolean = true): AllFileSets = runReadAction {
  findFileSets(root, true, true, includeContentNonIndexableSets, true, true, true).partition { fileSet ->
    fileSet !is WorkspaceFileSetWithCustomData<*> || fileSet.recursive
  }
}.let { (recursive, nonRecursive) -> AllFileSets(recursive, nonRecursive) }

@RequiresBackgroundThread
private fun WorkspaceFileIndex.iterateNonIndexableFilesImpl(roots: Set<VirtualFile>, filter: VirtualFileFilter, processor: ContentIterator): Boolean {
  for (root in roots) {
    val recursiveRootFileSets = allFileSets(root = root).recursive.toSet()
    val res = VfsUtilCore.visitChildrenRecursively(root, object : VirtualFileVisitor<Any?>() {
      override fun visitFileEx(file: VirtualFile): Result {
        ProgressManager.checkCanceled()
        val currentFileSets = allFileSets(root = file)
        return when {
          currentFileSets.recursive.size != recursiveRootFileSets.size -> SKIP_CHILDREN
          !recursiveRootFileSets.containsAll(currentFileSets.recursive) -> SKIP_CHILDREN
          !filter.accept(file) -> SKIP_CHILDREN
          currentFileSets.nonRecursive.any { it.kind.isIndexable } -> CONTINUE // skip only the current file, children can be non-indexable
          !processor.processFile(file) -> skipTo(root) // terminate processing
          else -> CONTINUE
        }
      }
    })
    if (res.skipChildren && res.skipToParent == root) return false
  }
  return true
}
