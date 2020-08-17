// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.diagnostic

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.diagnostic.dump.paths.IndexedFilePath
import com.intellij.util.indexing.diagnostic.dump.paths.IndexedFilePaths

/**
 * Accumulates indexing statistics for a set of indexable files.
 *
 * This class is not thread-safe. It must be synchronized by the clients.
 */
class IndexingJobStatistics(val fileSetName: String) {

  var totalIndexingTime: TimeNano = 0

  var numberOfIndexedFiles: Int = 0

  var numberOfTooLargeForIndexingFiles: Int = 0

  val statsPerIndexer = hashMapOf<String /* ID.name() */, StatsPerIndexer>()

  val statsPerFileType = hashMapOf<String /* File type name */, StatsPerFileType>()

  val tooLargeForIndexingFiles: LimitedPriorityQueue<TooLargeForIndexingFile> = LimitedPriorityQueue(5, compareBy { it.fileSize })

  val indexedFiles = arrayListOf<IndexedFilePath>()

  data class StatsPerIndexer(
    val indexingTime: TimeStats,
    var numberOfFiles: Int,
    var totalBytes: BytesNumber
  )

  data class StatsPerFileType(
    val indexingTime: TimeStats,
    val contentLoadingTime: TimeStats,
    var numberOfFiles: Int,
    var totalBytes: BytesNumber
  )

  fun addFileStatistics(
    file: VirtualFile,
    fileStatistics: FileIndexingStatistics,
    contentLoadingTime: Long,
    fileSize: Long,
    project: Project
  ) {
    numberOfIndexedFiles++
    fileStatistics.perIndexerTimes.forEach { (indexId, time) ->
      val stats = statsPerIndexer.getOrPut(indexId.name) {
        StatsPerIndexer(TimeStats(), 0, 0)
      }
      stats.indexingTime.addTime(time)
      stats.numberOfFiles++
      stats.totalBytes += fileSize
    }
    val fileTypeName = fileStatistics.fileType.name
    val stats = statsPerFileType.getOrPut(fileTypeName) {
      StatsPerFileType(TimeStats(), TimeStats(), 0, 0)
    }
    stats.contentLoadingTime.addTime(contentLoadingTime)
    stats.indexingTime.addTime(fileStatistics.indexingTime)
    stats.totalBytes += fileSize
    stats.numberOfFiles++
    if (IndexDiagnosticDumper.shouldDumpPathsOfIndexedFiles) {
      indexedFiles += IndexedFilePaths.createIndexedFilePath(file, project)
    }
  }

  fun addTooLargeForIndexingFile(
    file: VirtualFile,
    tooLargeForIndexingFile: TooLargeForIndexingFile,
    project: Project
  ) {
    numberOfIndexedFiles++
    numberOfTooLargeForIndexingFiles++
    tooLargeForIndexingFiles.addElement(tooLargeForIndexingFile)
    if (IndexDiagnosticDumper.shouldDumpPathsOfIndexedFiles) {
      indexedFiles += IndexedFilePaths.createIndexedFilePath(file, project)
    }
  }
}