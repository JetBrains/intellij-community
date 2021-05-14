// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.diagnostic.dto

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.intellij.util.indexing.diagnostic.dump.paths.PortableFilePath

@JsonInclude(JsonInclude.Include.NON_NULL)
data class JsonScanningStatistics(
  val providerName: String,
  val numberOfScannedFiles: Int,
  val numberOfSkippedFiles: Int,
  val numberOfFilesForIndexing: Int,
  val numberOfFilesFullyIndexedByInfrastructureExtensions: Int,
  val scanningTime: JsonDuration,
  val timeProcessingUpToDateFiles: JsonDuration,
  val timeUpdatingContentLessIndexes: JsonDuration,
  val timeIndexingWithoutContent: JsonDuration,

  // Available only if [com.intellij.util.indexing.diagnostic.IndexDiagnosticDumper.shouldDumpPathsOfIndexedFiles] is enabled.
  val scannedFiles: List<JsonScannedFile>?
) {
  data class JsonScannedFile(
    val path: PortableFilePath,
    val isUpToDate: Boolean,
    @JsonProperty("wfibe")
    val wasFullyIndexedByInfrastructureExtension: Boolean
  )
}