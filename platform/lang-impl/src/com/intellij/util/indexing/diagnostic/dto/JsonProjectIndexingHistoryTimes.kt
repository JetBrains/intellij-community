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

interface JsonProjectIndexingActivityHistoryTimes {
  val updatingStart: JsonDateTime
  val updatingEnd: JsonDateTime
  val totalSuspendedTime: JsonDuration
  val wasInterrupted: Boolean
}

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class JsonProjectScanningHistoryTimes(
  val indexingReason: String? = null,
  val scanningType: ScanningType = ScanningType.FULL,
  val totalUpdatingTime: JsonDuration = JsonDuration(),
  val indexingTime: JsonDuration = JsonDuration(),
  val creatingIteratorsTime: JsonDuration = JsonDuration(),
  val scanFilesTime: JsonDuration = JsonDuration(),
  val pushPropertiesTime: JsonDuration = JsonDuration(),
  val indexExtensionsTime: JsonDuration = JsonDuration(),

  override val updatingStart: JsonDateTime = JsonDateTime(),
  override val updatingEnd: JsonDateTime = JsonDateTime(),
  override val totalSuspendedTime: JsonDuration = JsonDuration(),
  override val wasInterrupted: Boolean = false
) : JsonProjectIndexingActivityHistoryTimes

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class JsonProjectDumbIndexingHistoryTimes(
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

  override val updatingStart: JsonDateTime = JsonDateTime(),
  override val updatingEnd: JsonDateTime = JsonDateTime(),
  override val totalSuspendedTime: JsonDuration = JsonDuration(),
  override val wasInterrupted: Boolean = false
) : JsonProjectIndexingActivityHistoryTimes