// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.diagnostic.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class JsonProjectIndexingHistoryTimes(
  val indexingReason: String? = null,
  val wasFullIndexing: Boolean = false,
  val totalUpdatingTime: JsonDuration = JsonDuration(),
  val indexingTime: JsonDuration = JsonDuration(),
  val contentLoadingTime: JsonDuration = JsonDuration(),
  val scanFilesTime: JsonDuration = JsonDuration(),
  val pushPropertiesTime: JsonDuration = JsonDuration(),
  val indexExtensionsTime: JsonDuration = JsonDuration(),

  val updatingStart: JsonDateTime = JsonDateTime(),
  val updatingEnd: JsonDateTime = JsonDateTime(),
  val totalSuspendedTime: JsonDuration = JsonDuration(),
  val wasInterrupted: Boolean = false
)