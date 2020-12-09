// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.diagnostic.dto

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class JsonScanningStatistics(
  val providerName: String,
  val numberOfScannedFiles: Int,
  val numberOfUpToDateFiles: Int,
  val numberOfFilesFullyIndexedByInfrastructureExtensions: Int,
  val scanningTime: JsonDuration,
  val timeProcessingUpToDateFiles: JsonDuration,
  val timeUpdatingContentLessIndexes: JsonDuration,
  val timeIndexingWithoutContent: JsonDuration
)