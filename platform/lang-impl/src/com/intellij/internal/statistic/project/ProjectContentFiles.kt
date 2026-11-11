// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ProjectContentFiles")

package com.intellij.internal.statistic.project

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.util.ArrayUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence
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
 * Returns the file type of [file] without a read of the file content.
 * Thus, it does not detect a `DetectedByContentFileType`.
 */
@ApiStatus.Internal
@RequiresReadLock
fun getFileTypeWithoutContent(file: VirtualFile): FileType =
  FileTypeManager.getInstance().getFileTypeByFile(file, ArrayUtil.EMPTY_BYTE_ARRAY)
