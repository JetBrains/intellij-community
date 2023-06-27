// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.diagnostic.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.intellij.util.indexing.diagnostic.ScanningType

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class JsonProjectIndexingHistoryTimes(
  val indexingReason: String? = null,
  val scanningType: ScanningType = ScanningType.FULL,
  val totalUpdatingTime: JsonDuration = JsonDuration(),
  val indexingTime: JsonDuration = JsonDuration(),
  val contentLoadingVisibleTime: JsonDuration = JsonDuration(),
  val creatingIteratorsTime: JsonDuration = JsonDuration(),
  val scanFilesTime: JsonDuration = JsonDuration(),
  val pushPropertiesTime: JsonDuration = JsonDuration(),
  val indexExtensionsTime: JsonDuration = JsonDuration(),
  val isAppliedAllValuesSeparately: Boolean = true,
  val separateApplyingIndexesVisibleTime: JsonDuration = JsonDuration(),

  val updatingStart: JsonDateTime = JsonDateTime(),
  val updatingEnd: JsonDateTime = JsonDateTime(),
  val totalSuspendedTime: JsonDuration = JsonDuration(),
  val wasInterrupted: Boolean = false
)

sealed interface JsonProjectIndexingActivityHistoryTimes {
  val updatingStart: JsonDateTime
  val updatingEnd: JsonDateTime
  val totalWallTimeWithPauses: JsonDuration
  val wallTimeOnPause: JsonDuration
  val totalWallTimeWithoutPauses: JsonDuration
    get() = JsonDuration(totalWallTimeWithPauses.nano - wallTimeOnPause.nano)
  val wasInterrupted: Boolean

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
  val collectingIndexableFilesTime: JsonDuration = JsonDuration(),
  val delayedPushPropertiesStageTime: JsonDuration = JsonDuration(),
  val indexExtensionsTime: JsonDuration = JsonDuration(),

  override val dumbModeStart: JsonDateTime? = null,
  override val dumbWallTimeWithPauses: JsonDuration = JsonDuration(),
  override val dumbWallTimeWithoutPauses: JsonDuration = JsonDuration(),

  override val updatingStart: JsonDateTime = JsonDateTime(),
  override val updatingEnd: JsonDateTime = JsonDateTime(),
  override val totalWallTimeWithPauses: JsonDuration = JsonDuration(),
  override val wallTimeOnPause: JsonDuration = JsonDuration(),
  override val wasInterrupted: Boolean = false
) : JsonProjectIndexingActivityHistoryTimes

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class JsonProjectDumbIndexingHistoryTimes(
  val scanningIds: Set<Long> = setOf(),
  val contentLoadingVisibleTime: JsonDuration = JsonDuration(),
  val retrievingChangedDuringIndexingFilesTime: JsonDuration = JsonDuration(),
  val isAppliedAllValuesSeparately: Boolean = true,
  val separateApplyingIndexesVisibleTime: JsonDuration = JsonDuration(),

  override val updatingStart: JsonDateTime = JsonDateTime(),
  override val updatingEnd: JsonDateTime = JsonDateTime(),
  override val totalWallTimeWithPauses: JsonDuration = JsonDuration(),
  override val wallTimeOnPause: JsonDuration = JsonDuration(),
  override val wasInterrupted: Boolean = false
) : JsonProjectIndexingActivityHistoryTimes {

  override val dumbModeStart: JsonDateTime
    get() = updatingStart

  override val dumbWallTimeWithPauses: JsonDuration
    get() = totalWallTimeWithPauses

  override val dumbWallTimeWithoutPauses: JsonDuration
    get() = totalWallTimeWithoutPauses
}