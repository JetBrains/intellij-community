// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.diagnostic

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.diagnostic.dump.paths.PortableFilePath
import com.intellij.util.indexing.diagnostic.dump.paths.PortableFilePaths

/**
 * Accumulates indexing statistics for a set of indexable files.
 *
 * This class is not thread-safe. It must be synchronized by the clients.
 */
class IndexingJobStatistics(private val project: Project, val fileSetName: String) {

  var indexingVisibleTime: TimeNano = 0

  var processingTimeInAllThreads: TimeNano = 0

  var contentLoadingTimeInAllThreads: TimeNano = 0

  val contentLoadingVisibleTime: TimeNano
    get() = if (processingTimeInAllThreads == 0L) 0
    else (indexingVisibleTime * contentLoadingTimeInAllThreads.toDouble() / processingTimeInAllThreads).toLong()

  var numberOfIndexedFiles: Int = 0

  var numberOfFilesFullyIndexedByExtensions: Int = 0

  var numberOfTooLargeForIndexingFiles: Int = 0

  val statsPerIndexer = hashMapOf<String /* ID.name() */, StatsPerIndexer>()

  val statsPerFileType = hashMapOf<String /* File type name */, StatsPerFileType>()

  val indexedFiles = arrayListOf<IndexedFile>()

  data class IndexedFile(val portableFilePath: PortableFilePath, val wasFullyIndexedByExtensions: Boolean)

  data class StatsPerIndexer(
    var indexingTime: TimeNano,
    var numberOfFiles: Int,
    var numberOfFilesIndexedByExtensions: Int,
    var totalBytes: BytesNumber
  )

  data class StatsPerFileType(
    var indexingTimeInAllThreads: TimeNano,
    var contentLoadingTimeInAllThreads: TimeNano,
    var numberOfFiles: Int,
    var totalBytes: BytesNumber
  )

  fun addFileStatistics(
    file: VirtualFile,
    fileStatistics: FileIndexingStatistics,
    processingTime: Long,
    contentLoadingTime: Long,
    fileSize: Long
  ) {
    numberOfIndexedFiles++
    if (fileStatistics.wasFullyIndexedByExtensions) {
      numberOfFilesFullyIndexedByExtensions++
    }
    processingTimeInAllThreads += processingTime
    contentLoadingTimeInAllThreads += contentLoadingTime
    val perIndexerTimes = fileStatistics.perIndexerUpdateTimes + fileStatistics.perIndexerDeleteTimes
    perIndexerTimes.forEach { (indexId, time) ->
      val stats = statsPerIndexer.getOrPut(indexId.name) {
        StatsPerIndexer(0, 0, 0, 0)
      }
      stats.indexingTime += time
      stats.numberOfFiles++
      if (indexId in fileStatistics.indexesProvidedByExtensions) {
        stats.numberOfFilesIndexedByExtensions++
      }
      stats.totalBytes += fileSize
    }
    val fileTypeName = fileStatistics.fileType.name
    val stats = statsPerFileType.getOrPut(fileTypeName) {
      StatsPerFileType(0, 0, 0, 0)
    }
    stats.contentLoadingTimeInAllThreads += contentLoadingTime
    stats.indexingTimeInAllThreads += perIndexerTimes.values.sum()
    stats.totalBytes += fileSize
    stats.numberOfFiles++
    if (IndexDiagnosticDumper.shouldDumpPathsOfIndexedFiles) {
      indexedFiles += IndexedFile(getIndexedFilePath(file), fileStatistics.wasFullyIndexedByExtensions)
    }
  }

  fun addTooLargeForIndexingFile(file: VirtualFile) {
    numberOfIndexedFiles++
    numberOfTooLargeForIndexingFiles++
    if (IndexDiagnosticDumper.shouldDumpPathsOfIndexedFiles) {
      indexedFiles += IndexedFile(getIndexedFilePath(file), false)
    }
  }

  private fun getIndexedFilePath(file: VirtualFile): PortableFilePath = try {
    PortableFilePaths.getPortableFilePath(file, project)
  }
  catch (e: Exception) {
    PortableFilePath.AbsolutePath(file.url)
  }

}