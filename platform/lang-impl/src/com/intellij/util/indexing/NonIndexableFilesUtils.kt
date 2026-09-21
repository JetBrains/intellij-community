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

private data class AllFileSets(val recursive: List<WorkspaceFileSet>, val nonRecursive: List<WorkspaceFileSet>)

private fun WorkspaceFileIndex.allIndexableFileSets(root: VirtualFile): AllFileSets {
  val indexableFileSets =
    findFileSets(root, true, true, false, true, true, false, true)

  return indexableFileSets
    .partition { fileSet -> fileSet !is WorkspaceFileSetWithCustomData<*> || fileSet.recursive }
    .let { (recursive, nonRecursive) -> AllFileSets(recursive, nonRecursive) }
}

private fun WorkspaceFileIndexEx.isExcludedOrInvalid(file: VirtualFile): Boolean {
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

private enum class SubtreeProcessingMode(val shouldProcessRoot: Boolean, val shouldProcessChildren: Boolean) {
  NONE(false, false),
  ROOT_ONLY(true, false),
  CHILDREN_ONLY(false, true),
  ROOT_AND_CHILDREN(true, true);

  companion object {
    fun fromFlags(shouldProcessRoot: Boolean, shouldProcessChildren: Boolean): SubtreeProcessingMode = when {
      shouldProcessRoot && shouldProcessChildren -> ROOT_AND_CHILDREN
      shouldProcessRoot -> ROOT_ONLY
      shouldProcessChildren -> CHILDREN_ONLY
      else -> NONE
    }
  }
}

private fun getSubtreeProcessingModeAt(
  file: VirtualFile,
  workspaceFileIndex: WorkspaceFileIndexEx,
  filter: VirtualFileFilter?,
): SubtreeProcessingMode {
  if (workspaceFileIndex.isExcludedOrInvalid(file)) return SubtreeProcessingMode.NONE

  val indexableFileSetsFromFile = workspaceFileIndex.allIndexableFileSets(file)
  if (indexableFileSetsFromFile.recursive.isNotEmpty()) return SubtreeProcessingMode.NONE

  val shouldProcessChildren = file.isValid && !file.isRecursiveOrCircularSymlink
  val shouldProcessRoot = indexableFileSetsFromFile.nonRecursive.isEmpty() &&
                          (filter == null || runReadActionBlocking { filter.accept(file) })

  return SubtreeProcessingMode.fromFlags(shouldProcessRoot, shouldProcessChildren)
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
        val subtreeProcessingMode = getSubtreeProcessingModeAt(file, this@iterateNonIndexableFilesImpl, filter)
        return when (subtreeProcessingMode) {
          SubtreeProcessingMode.NONE -> SKIP_CHILDREN
          SubtreeProcessingMode.CHILDREN_ONLY -> CONTINUE
          SubtreeProcessingMode.ROOT_ONLY -> if (processor.processFile(file)) SKIP_CHILDREN else skipTo(root)
          SubtreeProcessingMode.ROOT_AND_CHILDREN -> if (processor.processFile(file)) CONTINUE else skipTo(root)
        }
      }
    })
    if (res.skipChildren && res.skipToParent == root) return false
  }
  return true
}

/**
 * A file traversal that supports concurrent calls to [expand].
 *
 * The caller owns the file queue. Add [roots] to the queue before the traversal starts.
 */
@ApiStatus.Internal
interface ConcurrentFileTraversal {
  /**
   * Adds the child files to [consumer].
   *
   * @return `true` when the caller must process [file].
  */
  fun expand(file: VirtualFile, consumer: (List<VirtualFile>) -> Unit): Boolean
  val roots: Collection<VirtualFile>

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
    fun nonIndexableTraversal(
      project: Project,
      searchInLibraries: Boolean = true,
      filter: VirtualFileFilter? = null,
    ): ConcurrentFileTraversal {
      val workspaceFileIndex = WorkspaceFileIndexEx.getInstance(project)
      val roots = when {
        searchInLibraries -> workspaceFileIndex.nonIndexableRootsAsCacheAvoiding()
        else -> workspaceFileIndex.nonIndexableRootsAsCacheAvoiding { fileSet -> fileSet.kind.isContent }
      }
      return ConcurrentNonIndexableFileTraversal(project, roots, filter)
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
      val traversal = ConcurrentFileTraversal.nonIndexableTraversal(project, searchInLibraries, filter)
      return FilesDequeImpl(traversal)
    }
  }
}

private class FilesDequeImpl(
  private val traversal: ConcurrentFileTraversal,
) : FilesDeque {
  private val bfsQueue = ArrayDeque(traversal.roots)

  override fun computeNext(): VirtualFile? {
    while (bfsQueue.isNotEmpty()) {
      val file = bfsQueue.removeFirst()

      ProgressManager.checkCanceled()
      val shouldProcessRoot = traversal.expand(file, bfsQueue::addAll)
      if (!shouldProcessRoot) continue // skip only the current file, children can pass the filter

      return file
    }
    return null
  }
}

private class ConcurrentNonIndexableFileTraversal(
  private val project: Project,
  override val roots: Set<VirtualFile>,
  private val filter: VirtualFileFilter?,
) : ConcurrentFileTraversal {

  private val visitedRoots: MutableSet<VirtualFile> = ConcurrentHashMap.newKeySet()

  override fun expand(file: VirtualFile, consumer: (List<VirtualFile>) -> Unit): Boolean {
    if (file in visitedRoots) return false
    if (file in roots) {
      if (!visitedRoots.add(file)) return false
    }

    val subtreeProcessingMode = getSubtreeProcessingModeAt(file, WorkspaceFileIndexEx.getInstance(project), filter)

    if (subtreeProcessingMode.shouldProcessChildren) {
      consumer(file.children.asList())
    }

    return subtreeProcessingMode.shouldProcessRoot
  }
}
