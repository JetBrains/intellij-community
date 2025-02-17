// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.diagnostic.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.intellij.util.indexing.diagnostic.ScanningType

sealed interface JsonProjectIndexingActivityHistoryTimes {
  val updatingStart: JsonDateTime
  val updatingEnd: JsonDateTime
  val totalWallTimeWithPauses: JsonDuration
  val wallTimeOnPause: JsonDuration
  val totalWallTimeWithoutPauses: JsonDuration
    get() = JsonDuration(totalWallTimeWithPauses.nano - wallTimeOnPause.nano)
  val isCancelled: Boolean
  val cancellationReason: String?

  val dumbModeStart: JsonDateTime?
  val dumbWallTimeWithPauses: JsonDuration
  val dumbWallTimeWithoutPauses: JsonDuration
}

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class JsonProjectScanningHistoryTimes(
  val scanningReason: String? = null,
  val scanningType: ScanningType = ScanningType.FULL,
  val scanningId: Long = 0,
  val creatingIteratorsTime: JsonDuration = JsonDuration(),
  val concurrentHandlingWallTimeWithoutPauses: JsonDuration = JsonDuration(),
  val concurrentHandlingWallTimeWithPauses: JsonDuration = JsonDuration(),
  val concurrentHandlingSumOfThreadTimesWithPauses: JsonDuration = JsonDuration(),
  val concurrentIterationAndScannersApplicationSumOfThreadTimesWithPauses: JsonDuration = JsonDuration(),
  val concurrentFileCheckSumOfThreadTimesWithPauses: JsonDuration = JsonDuration(),
  val delayedPushPropertiesStageTime: JsonDuration = JsonDuration(),
  val indexExtensionsTime: JsonDuration = JsonDuration(),

  override val dumbModeStart: JsonDateTime? = null,
  override val dumbWallTimeWithPauses: JsonDuration = JsonDuration(),
  override val dumbWallTimeWithoutPauses: JsonDuration = JsonDuration(),

  override val updatingStart: JsonDateTime = JsonDateTime(),
  override val updatingEnd: JsonDateTime = JsonDateTime(),
  override val totalWallTimeWithPauses: JsonDuration = JsonDuration(),
  override val wallTimeOnPause: JsonDuration = JsonDuration(),
  override val isCancelled: Boolean = false,
  override val cancellationReason: String? = null,
) : JsonProjectIndexingActivityHistoryTimes

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class JsonProjectDumbIndexingHistoryTimes(
  val scanningIds: Set<Long> = setOf(),
  val contentLoadingVisibleTime: JsonDuration = JsonDuration(),
  val retrievingChangedDuringIndexingFilesTime: JsonDuration = JsonDuration(),
  val separateApplyingIndexesVisibleTime: JsonDuration = JsonDuration(),

  override val updatingStart: JsonDateTime = JsonDateTime(),
  override val updatingEnd: JsonDateTime = JsonDateTime(),
  override val totalWallTimeWithPauses: JsonDuration = JsonDuration(),
  override val wallTimeOnPause: JsonDuration = JsonDuration(),
  override val isCancelled: Boolean = false,
  override val cancellationReason: String? = null,
) : JsonProjectIndexingActivityHistoryTimes {

  override val dumbModeStart: JsonDateTime
    get() = updatingStart

  override val dumbWallTimeWithPauses: JsonDuration
    get() = totalWallTimeWithPauses

  override val dumbWallTimeWithoutPauses: JsonDuration
    get() = totalWallTimeWithoutPauses
}