// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.diagnostic.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class JsonProjectIndexingFileCount(
  val numberOfFileProviders: Int = 0,
  val numberOfScannedFiles: Int = 0,
  val numberOfFilesIndexedByInfrastructureExtensionsDuringScan: Int = 0,
  val numberOfFilesScheduledForIndexingAfterScan: Int = 0,
  val numberOfFilesIndexedByInfrastructureExtensionsDuringIndexingStage: Int = 0,
  val numberOfFilesIndexedWithLoadingContent: Int = 0
)