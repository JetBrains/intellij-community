// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.project

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.project.stateStore
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import org.jetbrains.annotations.ApiStatus

/**
 * The size of a project on disk, and the line count that the size implies.
 *
 * These are the only two measures of the repository profile that no collector reports. The platform already reports
 * the file count, the languages and the module totals. See `project.indexable.files`, `file.types` and
 * `project.structure`. Do not report those facts again here.
 *
 * Both values are an order of magnitude, not an exact number. The collector reports each one through
 * `EventFields.LogarithmicInt`, which rounds to 1, 2 and 5 per decade.
 *
 * @param sizeBytes The total size of the files under the content roots. It excludes `.git` and the build output,
 *   because neither is under a content root. It also excludes the project configuration files.
 * @param estimatedLoc The line count that [sizeBytes] implies, at [AVERAGE_BYTES_PER_LINE] bytes to a line.
 * @param limitReached `true` when the walk stopped at [FILES_COUNT_LIMIT]. Both values are then a lower bound.
 */
@ApiStatus.Internal
class ProjectSize internal constructor(
  val sizeBytes: Long,
  val estimatedLoc: Int,
  val limitReached: Boolean,
) {
  /** The size in megabytes, which is the unit the collector reports. */
  val sizeMb: Int
    get() = (sizeBytes / BYTES_PER_MB).toInt()
}

/**
 * Measures the project size with one walk of the content roots.
 *
 * The walk reads no file content. It sums the length that the virtual file system already holds, so the cost is one
 * pass over the content roots and no disk read per file.
 */
@ApiStatus.Internal
suspend fun computeProjectSize(project: Project): ProjectSize {
  val fileIndex = ProjectFileIndex.getInstance(project)
  val stateStore = project.stateStore
  val context = currentCoroutineContext()

  var filesCount = 0
  var sizeBytes = 0L

  fileIndex.iterateContent(
    ContentIterator { file ->
      if (!context.isActive) return@ContentIterator false
      filesCount++
      sizeBytes += file.length
      filesCount < FILES_COUNT_LIMIT
    },
    // Skip the project configuration files. They are not part of the repository the agent works on.
    VirtualFileFilter { file -> !file.isDirectory && !stateStore.isProjectFile(file) },
  )

  return ProjectSize(
    sizeBytes = sizeBytes,
    estimatedLoc = (sizeBytes / AVERAGE_BYTES_PER_LINE).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
    limitReached = filesCount >= FILES_COUNT_LIMIT,
  )
}

/**
 * The cap the attach-directory collector uses. A larger project reports a lower bound, which is still an order of
 * magnitude.
 */
private const val FILES_COUNT_LIMIT: Int = 1_000_000

/**
 * Source code averages close to this, and the count includes the indentation and the empty lines. The exact value
 * changes little, because the collector rounds the reported number by decade.
 */
private const val AVERAGE_BYTES_PER_LINE: Int = 30

private const val BYTES_PER_MB: Long = 1024L * 1024L
