// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.diagnostic.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.intellij.util.indexing.diagnostic.dump.paths.PortableFilePath

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class JsonScanningStatistics(
  val providerName: String = "",
  val numberOfScannedFiles: Int = 0,
  val numberOfSkippedFiles: Int = 0,
  val numberOfFilesForIndexing: Int = 0,
  val numberOfFilesFullyIndexedByInfrastructureExtensions: Int = 0,
  val filesFullyIndexedByInfrastructureExtensions: List<String> = emptyList(),
  val statusTime: JsonDuration = JsonDuration(0),
  val scanningTime: JsonDuration = JsonDuration(0),
  val timeProcessingUpToDateFiles: JsonDuration = JsonDuration(0),
  val timeUpdatingContentLessIndexes: JsonDuration = JsonDuration(0),
  val timeIndexingWithoutContent: JsonDuration = JsonDuration(0),

  /**
   * Available only if [com.intellij.util.indexing.diagnostic.IndexDiagnosticDumper.shouldDumpProviderRootPaths] is enabled.
   */
  val roots: List<String> = emptyList(),

  /**
   * Available only if [com.intellij.util.indexing.diagnostic.IndexDiagnosticDumper.shouldDumpPathsOfIndexedFiles] is enabled.
   */
  val scannedFiles: List<JsonScannedFile>? = null
) {
  @JsonIgnoreProperties(ignoreUnknown = true)
  data class JsonScannedFile(
    val path: PortableFilePath,
    val isUpToDate: Boolean,
    @JsonProperty("wfibe")
    val wasFullyIndexedByInfrastructureExtension: Boolean
  )
}