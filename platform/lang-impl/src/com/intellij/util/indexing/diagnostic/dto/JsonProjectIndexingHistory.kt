// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.diagnostic.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class JsonProjectIndexingHistory(
  val projectName: String = "",
  val times: JsonProjectIndexingHistoryTimes = JsonProjectIndexingHistoryTimes(),
  val fileCount: JsonProjectIndexingFileCount = JsonProjectIndexingFileCount(),
  val totalStatsPerFileType: List<JsonStatsPerFileType> = emptyList(),
  val totalStatsPerIndexer: List<JsonStatsPerIndexer> = emptyList(),
  val scanningStatistics: List<JsonScanningStatistics> = emptyList(),
  val fileProviderStatistics: List<JsonFileProviderIndexStatistics> = emptyList(),
  val visibleTimeToAllThreadTimeRatio: Double = 0.0
) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  data class JsonStatsPerFileType(
    val fileType: String = "",
    val partOfTotalProcessingTime: JsonPercentages = JsonPercentages(),
    val partOfTotalContentLoadingTime: JsonPercentages = JsonPercentages(),
    val totalNumberOfFiles: Int = 0,
    val totalFilesSize: JsonFileSize = JsonFileSize(),
    val indexingSpeed: JsonProcessingSpeed = JsonProcessingSpeed(),
    val biggestContributors: List<JsonBiggestFileTypeContributor> = emptyList()
  ) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class JsonBiggestFileTypeContributor(
      val providerName: String = "",
      val numberOfFiles: Int = 0,
      val totalFilesSize: JsonFileSize = JsonFileSize(),
      val partOfTotalProcessingTimeOfThisFileType: JsonPercentages = JsonPercentages()
    )
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  data class JsonStatsPerIndexer(
    val indexId: String = "",
    val partOfTotalIndexingTime: JsonPercentages = JsonPercentages(),
    val totalNumberOfFiles: Int = 0,
    val totalNumberOfFilesIndexedByExtensions: Int = 0,
    val totalFilesSize: JsonFileSize = JsonFileSize(),
    val indexingSpeed: JsonProcessingSpeed = JsonProcessingSpeed(),
    val snapshotInputMappingStats: JsonSnapshotInputMappingStats = JsonSnapshotInputMappingStats()
  ) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class JsonSnapshotInputMappingStats(
      val totalRequests: Long = 0,
      val totalMisses: Long = 0,
      val totalHits: Long = 0
    )
  }
}