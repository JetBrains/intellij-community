// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.diagnostic.dto

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class JsonProjectIndexingHistory(
  val projectName: String,
  val numberOfFileProviders: Int,
  val totalNumberOfFiles: Int,
  val totalNumberOfUpToDateFiles: Int,
  val totalNumberOfFilesIndexedByInfrastructureExtensions: Int,
  val times: JsonProjectIndexingHistoryTimes,
  val totalNumberOfTooLargeForIndexingFiles: Int,
  val totalStatsPerFileType: List<JsonStatsPerFileType>,
  val totalStatsPerIndexer: List<JsonStatsPerIndexer>,
  val scanningStatistics: List<JsonScanningStatistics>,
  val fileProviderStatistics: List<JsonFileProviderIndexStatistics>
) {

  data class JsonStatsPerFileType(
    val fileType: String,
    val partOfTotalIndexingTime: JsonPercentages,
    val partOfTotalContentLoadingTime: JsonPercentages,
    val totalNumberOfFiles: Int,
    val totalFilesSize: JsonFileSize,
    val indexingSpeed: JsonProcessingSpeed,
    val biggestContributors: List<JsonBiggestFileTypeContributor>
  ) {
    data class JsonBiggestFileTypeContributor(
      val providerName: String,
      val numberOfFiles: Int,
      val totalFilesSize: JsonFileSize,
      val partOfTotalIndexingTimeOfThisFileType: JsonPercentages
    )
  }

  data class JsonStatsPerIndexer(
    val indexId: String,
    val partOfTotalIndexingTime: JsonPercentages,
    val totalNumberOfFiles: Int,
    val totalNumberOfFilesIndexedByExtensions: Int,
    val totalFilesSize: JsonFileSize,
    val indexingSpeed: JsonProcessingSpeed
  )
}