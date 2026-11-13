// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ProjectContentFiles")

package com.intellij.internal.statistic.project

import com.intellij.openapi.application.readActionBlocking
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.checkCanceled
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.ProjectScope
import com.intellij.util.ArrayUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence
import com.intellij.util.indexing.FileBasedIndex
import org.jetbrains.annotations.ApiStatus
import java.util.function.Consumer

/**
 * Runs [processor] for each content file of [project] that [filter] accepts. The walk skips directories.
 *
 * The walk holds no read lock. Each call of [processor] runs in its own short read action.
 * Thus, a write action waits for one file at most.
 * A collector that calls this function must not override
 * [com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector.requiresReadAccess].
 *
 * The walk skips a file that is no longer valid, and it stops when [project] is disposed.
 */
@ApiStatus.Internal
@RequiresBackgroundThread
@RequiresReadLockAbsence
@JvmOverloads
fun processContentFilesInReadActions(
  project: Project,
  filter: VirtualFileFilter = VirtualFileFilter.ALL,
  processor: Consumer<in VirtualFile>,
) {
  ProjectFileIndex.getInstance(project).iterateContent(
    { file -> processSingleFile(file, project, processor) },
    { file -> !file.isDirectory && filter.accept(file) },
  )
}

private fun processSingleFile(
  file: VirtualFile,
  project: Project,
  processor: Consumer<in VirtualFile>,
): Boolean {
  ProgressManager.checkCanceled()
  return runReadActionBlocking {
    if (project.isDisposed) return@runReadActionBlocking false
    if (file.isValid) {
      processor.accept(file)
    }
    true
  }
}

/**
 * Runs [processor] for each indexed content file of [project], with the file type that [FileTypeIndex] holds.
 *
 * The data comes from [FileTypeIndex] over the content scope, so the function reads no file and walks no directory.
 * A file that the index does not hold is not processed. A file of a non-indexable file set is one example.
 * The type of a file is the indexed type, which the platform can detect by the content.
 *
 * [processor] runs in [readActionBlocking], in batches of [FILES_PER_READ_ACTION] files of one type.
 * Thus, a write action waits for one batch at most. A write action does not restart a batch, so [processor] sees each file once.
 * The function skips a file that is no longer valid.
 *
 * @return `false` when the process has no [FileBasedIndex], or when [project] is disposed. The caller then has no data.
 */
@ApiStatus.Internal
@RequiresReadLockAbsence
suspend fun processIndexedContentFilesByType(project: Project, processor: (FileType, VirtualFile) -> Unit): Boolean {
  val index = FileBasedIndex.getInstanceIfDefined() ?: return false
  val scope = ProjectScope.getContentScope(project)

  @Suppress("DEPRECATION")
  val fileTypes = readActionBlocking {
    if (project.isDisposed) null else index.getAllKeys(FileTypeIndex.NAME, project)
  } ?: return false

  for (fileType in fileTypes) {
    checkCanceled()

    @Suppress("DEPRECATION")
    val files = readActionBlocking {
      if (project.isDisposed) null else index.getContainingFilesIterator(FileTypeIndex.NAME, fileType, scope)
    } ?: return false

    var hasNext = true
    while (hasNext) {
      checkCanceled()
      hasNext = readActionBlocking {
        if (project.isDisposed) return@readActionBlocking null
        var left = FILES_PER_READ_ACTION
        while (left-- > 0 && files.hasNext()) {
          ProgressManager.checkCanceled()
          val file = files.next()
          if (file.isValid) {
            processor(fileType, file)
          }
        }
        files.hasNext()
      } ?: return false
    }
  }
  return true
}

/** The count of files of one type that [processIndexedContentFilesByType] gives to one read action. */
private const val FILES_PER_READ_ACTION = 1000

/**
 * Returns the file type of [file] without a read of the file content.
 * Thus, it does not detect a `DetectedByContentFileType`.
 */
@ApiStatus.Internal
@RequiresReadLock
fun getFileTypeWithoutContent(file: VirtualFile): FileType =
  FileTypeManager.getInstance().getFileTypeByFile(file, ArrayUtil.EMPTY_BYTE_ARRAY)
