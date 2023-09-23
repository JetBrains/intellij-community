// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.diagnostic

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.asSafely
import com.intellij.util.indexing.diagnostic.dump.paths.PortableFilePath
import com.intellij.util.indexing.diagnostic.dump.paths.PortableFilePaths

/**
 * Accumulates indexing statistics for a set of indexable files.
 *
 * This class is not thread-safe. It must be synchronized by the clients.
 */
class IndexingFileSetStatistics(private val project: Project, val fileSetName: String) {

  companion object {
    const val SLOW_FILES_LIMIT: Int = 10

    const val SLOW_FILE_PROCESSING_THRESHOLD_MS: Int = 500
  }

  var processingTimeInAllThreads: TimeNano = 0

  var contentLoadingTimeInAllThreads: TimeNano = 0

  var numberOfIndexedFiles: Int = 0

  var listOfFilesFullyIndexedByExtensions: ArrayList<String> = arrayListOf()

  var numberOfFilesFullyIndexedByExtensions: Int = 0

  var numberOfTooLargeForIndexingFiles: Int = 0

  val statsPerIndexer: HashMap<String, StatsPerIndexer> = hashMapOf()

  val statsPerFileType: HashMap<String, StatsPerFileType> = hashMapOf()

  val indexedFiles: ArrayList<IndexedFile> = arrayListOf()

  val slowIndexedFiles: LimitedPriorityQueue<SlowIndexedFile> = LimitedPriorityQueue(SLOW_FILES_LIMIT, compareBy { it.processingTime })

  var allSeparateApplicationTimeInAllThreads: TimeNano = 0

  data class IndexedFile(val portableFilePath: PortableFilePath, val wasFullyIndexedByExtensions: Boolean)

  data class StatsPerIndexer(
    var evaluateIndexValueChangerTime: TimeNano,
    var numberOfFiles: Int,
    var numberOfFilesIndexedByExtensions: Int,
    var totalBytes: NumberOfBytes
  )

  data class StatsPerFileType(
    var processingTimeInAllThreads: TimeNano,
    var contentLoadingTimeInAllThreads: TimeNano,
    var numberOfFiles: Int,
    var totalBytes: NumberOfBytes,
    val parentLanguages: MutableList<String> = mutableListOf()
  ) {
    constructor(fileType: FileType) : this(0, 0, 0, 0) {
      var language = fileType.asSafely<LanguageFileType>()?.language
      while (language != null){
        parentLanguages.add(language.id)
        language = language.baseLanguage
      }
    }
  }

  fun addFileStatistics(
    file: VirtualFile,
    fileStatistics: FileIndexingStatistics,
    processingTime: TimeNano,
    contentLoadingTime: TimeNano,
    fileSize: NumberOfBytes,
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
      StatsPerFileType(fileStatistics.fileType)
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