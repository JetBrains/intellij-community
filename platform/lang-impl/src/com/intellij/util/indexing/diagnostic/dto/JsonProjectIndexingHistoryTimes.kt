// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.diagnostic.dto

import com.fasterxml.jackson.annotation.JsonInclude

@Suppress("unused", "used for JSON")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class JsonProjectIndexingHistoryTimes(
  val totalUpdatingTime: JsonDuration,
  val indexingTime: JsonDuration,
  val scanFilesTime: JsonDuration,
  val pushPropertiesTime: JsonDuration,
  val indexExtensionsTime: JsonDuration,

  val updatingStart: JsonDateTime,
  val updatingEnd: JsonDateTime,
  val totalSuspendedTime: JsonDuration,
  val wasInterrupted: Boolean
)