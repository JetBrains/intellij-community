// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.diagnostic.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.intellij.util.indexing.diagnostic.dump.paths.PortableFilePath

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class JsonFileProviderIndexStatistics(
  val providerName: String = "",
  val totalNumberOfIndexedFiles: Int = 0,
  val totalNumberOfFilesFullyIndexedByExtensions: Int = 0,
  /**
   * It's actually total processing time. Was not renamed due to external usage.
   */
  val totalIndexingVisibleTime: JsonDuration = JsonDuration(0),
  val contentLoadingVisibleTime: JsonDuration = JsonDuration(0),
  val numberOfTooLargeForIndexingFiles: Int = 0,
  val slowIndexedFiles: List<JsonSlowIndexedFile> = emptyList(),
  val filesFullyIndexedByExtensions: List<String> = emptyList(),
  val isAppliedAllValuesSeparately: Boolean = true,
  /**
   * Is 0 when [isAppliedAllValuesSeparately] <=> [!FileBasedIndexImpl.isWritingIndexValuesSeparatedFromCounting]
   */
  val separateApplyingIndexesVisibleTime: JsonDuration = JsonDuration(0),
  /**
   * Available only if [com.intellij.util.indexing.diagnostic.IndexDiagnosticDumper.shouldDumpPathsOfIndexedFiles] is enabled.
   */
  val indexedFiles: List<JsonIndexedFile>? = null
) {
  @JsonIgnoreProperties(ignoreUnknown = true)
  data class JsonSlowIndexedFile(
    val fileName: String = "",
    val processingTime: JsonDuration = JsonDuration(0),
    val evaluationOfIndexValueChangerTime: JsonDuration = JsonDuration(0),
    val contentLoadingTime: JsonDuration = JsonDuration(0)
  )

  @JsonIgnoreProperties(ignoreUnknown = true)
  data class JsonIndexedFile(
    val path: PortableFilePath = PortableFilePath.AbsolutePath(""),
    @JsonProperty("wfibe")
    val wasFullyIndexedByExtensions: Boolean = false
  )
}