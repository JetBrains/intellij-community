// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  var processingTimeInAllThreads: TimeNano = 0

  var contentLoadingTimeInAllThreads: TimeNano = 0

  var numberOfIndexedFiles: Int = 0

  var listOfFilesFullyIndexedByExtensions = arrayListOf<String>()

  var numberOfFilesFullyIndexedByExtensions: Int = 0

  var numberOfTooLargeForIndexingFiles: Int = 0

  val statsPerIndexer = hashMapOf<String /* ID.name() */, StatsPerIndexer>()

  val statsPerFileType = hashMapOf<String /* File type name */, StatsPerFileType>()

  val indexedFiles = arrayListOf<IndexedFile>()

  val slowIndexedFiles = LimitedPriorityQueue<SlowIndexedFile>(SLOW_FILES_LIMIT, compareBy { it.processingTime })

  var allValuesAppliedSeparately = true
  var allSeparateApplicationTimeInAllThreads: TimeNano = 0 //is 0 when !allValuesAppliedSeparately

  data class IndexedFile(val portableFilePath: PortableFilePath, val wasFullyIndexedByExtensions: Boolean)

  data class StatsPerIndexer(
    var evaluateIndexValueChangerTime: TimeNano,
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
    fileSize: BytesNumber,
    valuesAppliedSeparately: Boolean,
    separateApplicationTime: TimeNano
  ) {
    numberOfIndexedFiles++
    if (fileStatistics.wasFullyIndexedByExtensions) {
      numberOfFilesFullyIndexedByExtensions++
      if (IndexDiagnosticDumper.shouldDumpPathsOfFilesIndexedByInfrastructureExtensions) {
        listOfFilesFullyIndexedByExtensions.add(file.toString())
      }
    }
    processingTimeInAllThreads += processingTime
    contentLoadingTimeInAllThreads += contentLoadingTime
    val perIndexerEvaluationOfValueChangerTimes = fileStatistics.perIndexerEvaluateIndexValueTimes.toMutableMap()
    fileStatistics.perIndexerEvaluatingIndexValueRemoversTimes.forEach { (indexId, time) ->
      perIndexerEvaluationOfValueChangerTimes[indexId] = time + perIndexerEvaluationOfValueChangerTimes.getOrDefault(indexId, 0)
    }
    perIndexerEvaluationOfValueChangerTimes.forEach { (indexId, time) ->
      val stats = statsPerIndexer.getOrPut(indexId.name) {
        StatsPerIndexer(0, 0, 0, 0)
      }
      stats.evaluateIndexValueChangerTime += time
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
    val evaluationOfIndexValueChangerTime = perIndexerEvaluationOfValueChangerTimes.values.sum()
    stats.processingTimeInAllThreads += processingTime
    stats.totalBytes += fileSize
    stats.numberOfFiles++
    if (IndexDiagnosticDumper.shouldDumpPathsOfIndexedFiles) {
      indexedFiles += IndexedFile(getIndexedFilePath(file), fileStatistics.wasFullyIndexedByExtensions)
    }
    if (processingTime > SLOW_FILE_PROCESSING_THRESHOLD_MS * 1_000_000) {
      slowIndexedFiles.addElement(SlowIndexedFile(file.name, processingTime, evaluationOfIndexValueChangerTime, contentLoadingTime))
    }
    allValuesAppliedSeparately = allValuesAppliedSeparately && valuesAppliedSeparately
    allSeparateApplicationTimeInAllThreads += separateApplicationTime
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