// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

sealed interface JsonProjectIndexingActivityFileCount

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class JsonProjectScanningFileCount(
  val numberOfFileProviders: Int = 0,
  val numberOfScannedFiles: Int = 0,
  val numberOfFilesIndexedByInfrastructureExtensionsDuringScan: Int = 0,
  val numberOfFilesScheduledForIndexingAfterScan: Int = 0
) : JsonProjectIndexingActivityFileCount

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class JsonProjectDumbIndexingFileCount(
  val numberOfChangedDuringIndexingFiles: Int = 0,
  val numberOfFilesIndexedByInfrastructureExtensionsDuringIndexingStage: Int = 0,
  val numberOfFilesIndexedWithLoadingContent: Int = 0
) : JsonProjectIndexingActivityFileCount