// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.diagnostic.dto

import com.fasterxml.jackson.annotation.JsonInclude
import com.intellij.util.indexing.diagnostic.dump.paths.PortableFilePath

@JsonInclude(JsonInclude.Include.NON_NULL)
data class JsonFileProviderIndexStatistics(
  val providerName: String,
  val totalNumberOfFiles: Int,
  val totalIndexingTime: JsonDuration,
  val numberOfTooLargeForIndexingFiles: PositiveInt,
  val tooLargeForIndexingFiles: List<JsonTooLargeForIndexingFile>?,
  val statsPerFileType: List<JsonStatsPerFileType>,
  val statsPerIndexer: List<JsonStatsPerIndexer>,
  val fastIndexers: List<String /* Index ID */>,
  // Available only if [com.intellij.util.indexing.diagnostic.IndexDiagnosticDumper.shouldDumpPathsOfIndexedFiles] is enabled.
  val indexedFiles: List<PortableFilePath>?
) {

  data class JsonStatsPerFileType(
    val fileType: String,
    val numberOfFiles: Int,
    val totalFilesSize: JsonFileSize,
    val partOfTotalIndexingTime: JsonPercentages,
    val partOfTotalContentLoadingTime: JsonPercentages
  )

  data class JsonStatsPerIndexer(
    val indexId: String,
    val numberOfFiles: Int,
    val numberOfFilesIndexedByExtensions: Int,
    val partOfTotalIndexingTime: JsonPercentages
  )
}