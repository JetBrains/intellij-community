// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("NonIndexableFilesUtils")

package com.intellij.util.indexing

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runReadActionBlocking
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
import java.util.concurrent.ConcurrentHashMap


/**
 * @return all workspace model's roots that are not indexable from fileSets matching [fileSetFilter].
 * Method tries to return those roots as [com.intellij.openapi.vfs.newvfs.CacheAvoidingVirtualFile]s, so tree-traversal starting
 * from them won't trash VFS cache with useless entries
 * @see WorkspaceFileKind.CONTENT_NON_INDEXABLE
 * @see WorkspaceFileKind.EXTERNAL_NON_INDEXABLE
 * @see WorkspaceFileIndexEx.isIndexable
 */
@ApiStatus.Internal
@RequiresBackgroundThread(generateAssertion = false /* IJPL-115548 */)
@RequiresReadLock(generateAssertion = false /* IJPL-115548 */)
fun WorkspaceFileIndexEx.nonIndexableRootsAsCacheAvoiding(fileSetFilter: (WorkspaceFileSet) -> Boolean = { true }): Set<VirtualFile> {
  val roots = mutableSetOf<VirtualFile>()
  visitFileSets { fileSet, _ ->
    val root = fileSet.root
    if (!fileSet.kind.isIndexable && fileSetFilter(fileSet)) {
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
    ReadAction.nonBlocking<Set<VirtualFile>> { workspaceFileIndex.nonIndexableRootsAsCacheAvoiding() }.executeSynchronously()
  return workspaceFileIndex.iterateNonIndexableFilesImpl(roots, inputFilter, processor)
}

@ApiStatus.Internal
data class AllFileSets(val recursive: List<WorkspaceFileSet>, val nonRecursive: List<WorkspaceFileSet>)

@ApiStatus.Internal
fun WorkspaceFileIndex.allIndexableFileSets(root: VirtualFile): AllFileSets {
  val indexableFileSets =
    findFileSets(root, true, true, false, true, true, false, true)

  return indexableFileSets
    .partition { fileSet -> fileSet !is WorkspaceFileSetWithCustomData<*> || fileSet.recursive }
    .let { (recursive, nonRecursive) -> AllFileSets(recursive, nonRecursive) }
}

@ApiStatus.Internal
fun WorkspaceFileIndexEx.isExcludedOrInvalid(file: VirtualFile): Boolean {
  val info =
    getFileInfo(file, true, true, true, true, true, true, true)

  return when (info) {
    NonWorkspace.EXCLUDED -> true
    NonWorkspace.IGNORED -> true
    NonWorkspace.INVALID -> true
    NonWorkspace.NOT_UNDER_ROOTS -> true
    else -> false
  }
}

@RequiresBackgroundThread(generateAssertion = false /* IJPL-115548 */)
private fun WorkspaceFileIndexEx.iterateNonIndexableFilesImpl(
  roots: Set<VirtualFile>,
  filter: VirtualFileFilter?,
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
          filter != null && !runReadActionBlocking { filter.accept(file) } -> CONTINUE // skip only the current file, children can pass the filter
          !processor.processFile(file) -> skipTo(root) // terminate processing
          else -> CONTINUE
        }
      }
    })
    if (res.skipChildren && res.skipToParent == root) return false
  }
  return true
}

/**
 * Concurrent-code-friendly version of [FilesDeque]
 */
@ApiStatus.Internal
interface ConcurrentFilesDeque {
  /**
   * Computes the following elements and puts them to [consumer].
   * Client should maintain a thread-safe queue of VirtualFiles. Client should first invoke [initialItems] to add initial elements
   * to the queue, and then supply elements from the head of the queue to [computeNext] method in one or multiple threads
   * @return `true` if the [file] itself should be processed, `false` if the [file] itself should be skipped.
   */
  fun computeNext(file: VirtualFile, consumer: (List<VirtualFile>) -> Unit): Boolean
  fun initialItems(): Collection<VirtualFile>

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
    @RequiresReadLock(generateAssertion = false /* IJPL-115548 */)
    @RequiresBackgroundThread(generateAssertion = false /* IJPL-115548 */)
    fun nonIndexableDequeue(
      project: Project,
      searchInLibraries: Boolean = true,
      filter: VirtualFileFilter? = null,
    ): ConcurrentFilesDeque {
      val workspaceFileIndex = WorkspaceFileIndexEx.getInstance(project)
      val roots = when {
        searchInLibraries -> workspaceFileIndex.nonIndexableRootsAsCacheAvoiding()
        else -> workspaceFileIndex.nonIndexableRootsAsCacheAvoiding { fileSet -> fileSet.kind.isContent }
      }
      return ConcurrentNonIndexableFilesDequeImpl(project, roots, filter)
    }
  }
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
    @RequiresReadLock(generateAssertion = false /* IJPL-115548 */)
    @RequiresBackgroundThread(generateAssertion = false /* IJPL-115548 */)
    fun nonIndexableDequeue(
      project: Project,
      searchInLibraries: Boolean = true,
      filter: VirtualFileFilter? = null,
    ): FilesDeque {
      val concurrentDeque = ConcurrentFilesDeque.nonIndexableDequeue(project, searchInLibraries, filter)
      return FilesDequeImpl(concurrentDeque)
    }
  }
}

@ApiStatus.Internal
class FilesDequeImpl internal constructor(
  private val concurrentDeque: ConcurrentFilesDeque,
) : FilesDeque {
  private val bfsQueue = ArrayDeque(concurrentDeque.initialItems())

  override fun computeNext(): VirtualFile? {
    while (bfsQueue.isNotEmpty()) {
      val file = bfsQueue.removeFirst()

      val shouldProcessRoot = concurrentDeque.computeNext(file, bfsQueue::addAll)
      if (!shouldProcessRoot) continue // skip only the current file, children can pass the filter

      return file
    }
    return null
  }
}

@ApiStatus.Internal
class ConcurrentNonIndexableFilesDequeImpl internal constructor(
  private val project: Project,
  private val roots: Set<VirtualFile>,
  private val filter: VirtualFileFilter?,
) : ConcurrentFilesDeque {

  private data class SubtreeProcessingMode(val shouldProcessRoot: Boolean, val shouldProcessChildren: Boolean){
    companion object {
      val NONE = SubtreeProcessingMode(false, false)
    }
  }

  private fun getSubtreeProcessingModeAt(file: VirtualFile, workspaceFileIndex: WorkspaceFileIndexEx): SubtreeProcessingMode {
    if (workspaceFileIndex.isExcludedOrInvalid(file)) return SubtreeProcessingMode.NONE

    val indexableFileSetsFromFile = workspaceFileIndex.allIndexableFileSets(file)
    if (indexableFileSetsFromFile.recursive.isNotEmpty()) return SubtreeProcessingMode.NONE

    val shouldProcessChildren = (file.isValid && !file.isRecursiveOrCircularSymlink)
    val shouldProcessRoot = indexableFileSetsFromFile.nonRecursive.isEmpty() // skip only the current file, children can be non-indexable

    return SubtreeProcessingMode(shouldProcessRoot, shouldProcessChildren)
  }

  private val visitedRoots: MutableSet<VirtualFile> = ConcurrentHashMap.newKeySet()

  override fun computeNext(file: VirtualFile, consumer: (List<VirtualFile>) -> Unit): Boolean {
    if (file in visitedRoots) return false
    if (file in roots) {
      if (!visitedRoots.add(file)) return false
    }

    val subtreeProcessingMode = getSubtreeProcessingModeAt(file, WorkspaceFileIndexEx.getInstance(project))

    if (subtreeProcessingMode.shouldProcessChildren) {
      consumer(file.children.asList())
    }

    if (subtreeProcessingMode.shouldProcessRoot) {
      return filter == null || runReadActionBlocking { filter.accept(file) }
    }
    else {
      return false
    }
  }

  override fun initialItems(): Collection<VirtualFile> {
    return roots
  }
}
