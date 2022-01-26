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
class IndexingFileSetStatistics(private val project: Project, val fileSetName: String) {

  companion object {
    const val SLOW_FILES_LIMIT = 10

    const val SLOW_FILE_PROCESSING_THRESHOLD_MS = 500
  }

  var indexingVisibleTime: TimeNano = 0

  var processingTimeInAllThreads: TimeNano = 0

  var contentLoadingTimeInAllThreads: TimeNano = 0

  val contentLoadingVisibleTime: TimeNano
    get() = if (processingTimeInAllThreads == 0L) 0
    else (indexingVisibleTime * contentLoadingTimeInAllThreads.toDouble() / processingTimeInAllThreads).toLong()

  var numberOfIndexedFiles: Int = 0

  var listOfFilesFullyIndexedByExtensions = arrayListOf<String>()

  var numberOfFilesFullyIndexedByExtensions: Int = 0

  var numberOfTooLargeForIndexingFiles: Int = 0

  val statsPerIndexer = hashMapOf<String /* ID.name() */, StatsPerIndexer>()

  val statsPerFileType = hashMapOf<String /* File type name */, StatsPerFileType>()

  val indexedFiles = arrayListOf<IndexedFile>()

  val slowIndexedFiles = LimitedPriorityQueue<SlowIndexedFile>(SLOW_FILES_LIMIT, compareBy { it.processingTime })

  data class IndexedFile(val portableFilePath: PortableFilePath, val wasFullyIndexedByExtensions: Boolean)

  data class StatsPerIndexer(
    var indexingTime: TimeNano,
    var numberOfFiles: Int,
    var numberOfFilesIndexedByExtensions: Int,
    var totalBytes: BytesNumber
  )

  data class StatsPerFileType(
    var processingTimeInAllThreads: TimeNano,
    var contentLoadingTimeInAllThreads: TimeNano,
    var numberOfFiles: Int,
    var totalBytes: BytesNumber
  )

  fun addFileStatistics(
    file: VirtualFile,
    fileStatistics: FileIndexingStatistics,
    processingTime: TimeNano,
    contentLoadingTime: TimeNano,
    fileSize: BytesNumber
  ) {
    numberOfIndexedFiles++
    if (fileStatistics.wasFullyIndexedByExtensions) {
      numberOfFilesFullyIndexedByExtensions++
      listOfFilesFullyIndexedByExtensions.add(file.toString())
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
    val indexingTime = perIndexerTimes.values.sum()
    stats.processingTimeInAllThreads += processingTime
    stats.totalBytes += fileSize
    stats.numberOfFiles++
    if (IndexDiagnosticDumper.shouldDumpPathsOfIndexedFiles) {
      indexedFiles += IndexedFile(getIndexedFilePath(file), fileStatistics.wasFullyIndexedByExtensions)
    }
    if (processingTime > SLOW_FILE_PROCESSING_THRESHOLD_MS * 1_000_000) {
      slowIndexedFiles.addElement(SlowIndexedFile(file.name, processingTime, indexingTime, contentLoadingTime))
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