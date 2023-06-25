// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
    /**
     * bytes to total (not visible) processing (not just indexing) time
     */
    val totalProcessingSpeed: JsonProcessingSpeed = JsonProcessingSpeed(),
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
    val indexValueChangerEvaluationSpeed: JsonProcessingSpeed = JsonProcessingSpeed(),
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

sealed interface JsonProjectIndexingActivityHistory {
  val projectName: String
  val times: JsonProjectIndexingActivityHistoryTimes
  val fileCount: JsonProjectIndexingActivityFileCount
}

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class JsonProjectScanningHistory(
  override val projectName: String = "",
  override val times: JsonProjectScanningHistoryTimes = JsonProjectScanningHistoryTimes(),
  override val fileCount: JsonProjectScanningFileCount = JsonProjectScanningFileCount(),
  val scanningStatistics: List<JsonScanningStatistics> = emptyList()
) : JsonProjectIndexingActivityHistory

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class JsonProjectDumbIndexingHistory(
  override val projectName: String = "",
  override val times: JsonProjectDumbIndexingHistoryTimes = JsonProjectDumbIndexingHistoryTimes(),
  override val fileCount: JsonProjectDumbIndexingFileCount = JsonProjectDumbIndexingFileCount(),
  val totalStatsPerFileType: List<JsonStatsPerFileType> = emptyList(),
  val totalStatsPerBaseLanguage: List<JsonStatsPerParentLanguage> = emptyList(), //todo[lene] use it!
  val totalStatsPerIndexer: List<JsonStatsPerIndexer> = emptyList(),
  val statisticsOfChangedDuringIndexingFiles: JsonChangedFilesDuringIndexingStatistics = JsonChangedFilesDuringIndexingStatistics(),
  val fileProviderStatistics: List<JsonFileProviderIndexStatistics> = emptyList(),
  val visibleTimeToAllThreadTimeRatio: Double = 0.0
) : JsonProjectIndexingActivityHistory {

  @JsonIgnoreProperties(ignoreUnknown = true)
  data class JsonStatsPerFileType(
    val fileType: String = "",
    val partOfTotalProcessingTime: JsonPercentages = JsonPercentages(),
    val partOfTotalContentLoadingTime: JsonPercentages = JsonPercentages(),
    val totalNumberOfFiles: Int = 0,
    val totalFilesSize: JsonFileSize = JsonFileSize(),
    /**
     * bytes to total (not visible) processing (not just indexing) time
     */
    val totalProcessingSpeed: JsonProcessingSpeed = JsonProcessingSpeed(),
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
  data class JsonStatsPerParentLanguage(
    val language: String = "",
    val partOfTotalProcessingTime: JsonPercentages = JsonPercentages(),
    val partOfTotalContentLoadingTime: JsonPercentages = JsonPercentages(),
    val totalNumberOfFiles: Int = 0,
    val totalFilesSize: JsonFileSize = JsonFileSize(),
    /**
     * bytes to total (not visible) processing (not just indexing) time
     */
    val totalProcessingSpeed: JsonProcessingSpeed = JsonProcessingSpeed(),
  )

  @JsonIgnoreProperties(ignoreUnknown = true)
  data class JsonStatsPerIndexer(
    val indexId: String = "",
    val partOfTotalIndexingTime: JsonPercentages = JsonPercentages(),
    val totalNumberOfFiles: Int = 0,
    val totalNumberOfFilesIndexedByExtensions: Int = 0,
    val totalFilesSize: JsonFileSize = JsonFileSize(),
    val indexValueChangerEvaluationSpeed: JsonProcessingSpeed = JsonProcessingSpeed(),
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
