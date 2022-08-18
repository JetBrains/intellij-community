// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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