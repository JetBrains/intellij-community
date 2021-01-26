// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.diagnostic.dto

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.intellij.util.indexing.diagnostic.dump.paths.PortableFilePath

@JsonInclude(JsonInclude.Include.NON_NULL)
data class JsonFileProviderIndexStatistics(
  val providerName: String,
  val totalNumberOfIndexedFiles: Int,
  val totalNumberOfFilesFullyIndexedByExtensions: Int,
  val totalIndexingTime: JsonDuration,
  val numberOfTooLargeForIndexingFiles: Int,
  // Available only if [com.intellij.util.indexing.diagnostic.IndexDiagnosticDumper.shouldDumpPathsOfIndexedFiles] is enabled.
  val indexedFiles: List<JsonIndexedFile>?
) {
  data class JsonIndexedFile(
    val path: PortableFilePath,
    @JsonProperty("wfibe")
    val wasFullyIndexedByExtensions: Boolean
  )
}