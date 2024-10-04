// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.diagnostic.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.intellij.openapi.vfs.limits.FileSizeLimit
import com.intellij.util.indexing.UnindexedFilesUpdater

@JsonIgnoreProperties(ignoreUnknown = true)
data class JsonRuntimeInfo(
  val maxMemory: Long = 0,
  val numberOfProcessors: Int = 0,
  val maxNumberOfIndexingThreads: Int = 0,
  val maxSizeOfFileForIntelliSense: Int = 0,
  val maxSizeOfFileForContentLoading: Int = 0
) {
  companion object {
    fun create(): JsonRuntimeInfo {
      val runtime = Runtime.getRuntime()
      return JsonRuntimeInfo(
        runtime.maxMemory(),
        runtime.availableProcessors(),
        UnindexedFilesUpdater.getMaxNumberOfIndexingThreads(),
        FileSizeLimit.getIntellisenseLimit(),
        FileSizeLimit.getContentLoadLimit()
      )
    }
  }
}