// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.diagnostic

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem

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

  val indexedFiles = arrayListOf<String /* File short path */>()

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
    fileSize: Long
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
      indexedFiles += getFilePath(file)
    }
  }

  fun addTooLargeForIndexingFile(
    file: VirtualFile,
    tooLargeForIndexingFile: TooLargeForIndexingFile
  ) {
    numberOfIndexedFiles++
    numberOfTooLargeForIndexingFiles++
    tooLargeForIndexingFiles.addElement(tooLargeForIndexingFile)
    if (IndexDiagnosticDumper.shouldDumpPathsOfIndexedFiles) {
      indexedFiles += getFilePath(file)
    }
  }

  private fun getFilePath(file: VirtualFile): String {
    val fileSystem = file.fileSystem
    if (fileSystem is ArchiveFileSystem) {
      val localArchiveFile = fileSystem.getLocalByEntry(file)
      if (localArchiveFile != null) {
        return localArchiveFile.name + ":" + file.name
      }
    }
    return file.name
  }
}