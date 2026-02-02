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
import com.intellij.openapi.vfs.newvfs.NewVirtualFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSet
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetWithCustomData
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexEx
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileInternalInfo.NonWorkspace
import org.jetbrains.annotations.ApiStatus


/**
 * @return all workspace model's roots that are not indexable.
 * Method tries to return those roots as [com.intellij.openapi.vfs.newvfs.CacheAvoidingVirtualFile]s, so tree-traversal starting
 * from them won't trash VFS cache with useless entries
 * @see WorkspaceFileKind.CONTENT_NON_INDEXABLE
 * @see WorkspaceFileKind.EXTERNAL_NON_INDEXABLE
 * @see WorkspaceFileIndexEx.isIndexable
 */
@ApiStatus.Internal
@RequiresBackgroundThread
@RequiresReadLock
fun WorkspaceFileIndexEx.nonIndexableRoots(): Set<VirtualFile> {
  val roots = mutableSetOf<VirtualFile>()
  visitFileSets { fileSet, _ ->
    val root = fileSet.root
    if (!fileSet.kind.isIndexable) {
      //Wrap the root in cache-avoiding, so file-tree hierarchy walking starting from this root will not trash VFS cache with
      // new entries -- it makes perfect sense for non-indexable because such file-sets are rarely accessed.
      roots.add(NewVirtualFile.asCacheAvoiding(root))
    }
  }
  return roots
}

internal fun iterateNonIndexableFilesImpl(project: Project, inputFilter: VirtualFileFilter?, processor: ContentIterator): Boolean {
  val workspaceFileIndex = WorkspaceFileIndexEx.getInstance(project)
  val roots: Set<VirtualFile> =
    ReadAction.nonBlocking<Set<VirtualFile>> { workspaceFileIndex.nonIndexableRoots() }.executeSynchronously()
  return workspaceFileIndex.iterateNonIndexableFilesImpl(roots, inputFilter ?: VirtualFileFilter.ALL, processor)
}

private data class AllFileSets(val recursive: List<WorkspaceFileSet>, val nonRecursive: List<WorkspaceFileSet>)

private fun WorkspaceFileIndex.allIndexableFileSets(root: VirtualFile): AllFileSets {
  val indexableFileSets = runReadAction {
    findFileSets(root, true, true, false, true, true, false, true)
  }
  return indexableFileSets
    .partition { fileSet -> fileSet !is WorkspaceFileSetWithCustomData<*> || fileSet.recursive }
    .let { (recursive, nonRecursive) -> AllFileSets(recursive, nonRecursive) }
}

private fun WorkspaceFileIndexEx.isExcludedOrInvalid(file: VirtualFile): Boolean {
  val info = runReadAction {
    getFileInfo(file, true, true, true, true, true, true, true)
  }
  return when (info) {
    NonWorkspace.EXCLUDED -> true
    NonWorkspace.IGNORED -> true
    NonWorkspace.INVALID -> true
    NonWorkspace.NOT_UNDER_ROOTS -> true
    else -> false
  }
}

@RequiresBackgroundThread
private fun WorkspaceFileIndexEx.iterateNonIndexableFilesImpl(
  roots: Set<VirtualFile>,
  filter: VirtualFileFilter,
  processor: ContentIterator,
): Boolean {
  for (root in roots) {
    val res = VfsUtilCore.visitChildrenRecursively(root, object : VirtualFileVisitor<Any?>() {
      override fun visitFileEx(file: VirtualFile): Result {
        ProgressManager.checkCanceled()
        if (isExcludedOrInvalid(file)) return SKIP_CHILDREN
        val currentIndexableFileSets = allIndexableFileSets(root = file)
        return when {
          currentIndexableFileSets.recursive.isNotEmpty() -> SKIP_CHILDREN
          currentIndexableFileSets.nonRecursive.isNotEmpty() -> CONTINUE // skip only the current file, children can be non-indexable
          !runReadAction { filter.accept(file) } -> CONTINUE // skip only the current file, children can pass the filter
          !processor.processFile(file) -> skipTo(root) // terminate processing
          else -> CONTINUE
        }
      }
    })
    if (res.skipChildren && res.skipToParent == root) return false
  }
  return true
}


@ApiStatus.Internal
interface FilesDeque {
  fun computeNext(): VirtualFile?

  companion object {

    /**
     * Use [FileBasedIndex.iterateNonIndexableFiles] instead.
     *
     * This method is only for rare specific use-cases,
     * where we need to process non-indexable files in a non-blocking read action, such as find-in-files
     */
    @ApiStatus.Internal
    @JvmStatic
    @JvmOverloads
    @RequiresReadLock
    @RequiresBackgroundThread
    fun nonIndexableDequeue(project: Project, filter: VirtualFileFilter = VirtualFileFilter.ALL): FilesDeque {
      return NonIndexableFilesDequeImpl(project, WorkspaceFileIndexEx.getInstance(project).nonIndexableRoots(), filter)
    }
  }
}

private class NonIndexableFilesDequeImpl(
  private val project: Project,
  private val roots: Set<VirtualFile>,
  private val filter: VirtualFileFilter,
) : FilesDeque {
  private val bfsQueue: ArrayDeque<VirtualFile> = ArrayDeque(roots)
  private val visitedRoots: MutableSet<VirtualFile> = mutableSetOf()

  override fun computeNext(): VirtualFile? {
    while (bfsQueue.isNotEmpty()) {
      val file = bfsQueue.removeFirst()

      if (file in visitedRoots) continue
      if (file in roots) visitedRoots.add(file)

      val workspaceFileIndex = WorkspaceFileIndexEx.getInstance(project)
      if (workspaceFileIndex.isExcludedOrInvalid(file)) continue
      val indexableFileSets = WorkspaceFileIndexEx.getInstance(project).allIndexableFileSets(file)

      if (indexableFileSets.recursive.isNotEmpty()) continue // skip the current file and their children
      if (file.isValid && !file.isRecursiveOrCircularSymlink) {
        val children = file.children
        if (children != null) bfsQueue.addAll(children)
      }
      if (indexableFileSets.nonRecursive.isNotEmpty()) continue // skip only the current file, children can be non-indexable
      if (!runReadAction { filter.accept(file) }) continue // skip only the current file, children can pass the filter

      return file
    }
    return null
  }
}
