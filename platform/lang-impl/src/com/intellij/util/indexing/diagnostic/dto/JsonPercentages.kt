// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.diagnostic.dto

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class JsonPercentages(val part: Long = 0, val total: Long = 0) {

  val partition: Double
    @JsonIgnore
    get() = if (total == 0L) 1.0 else part.toDouble() / total

  val doublePercentages: Double
    @JsonIgnore
    get() = partition * 100

  fun presentablePercentages(): String =
    when {
      total == 0L -> "100%"
      part == 0L -> "0%"
      part * 100 < total -> "< 1%"
      else -> String.format("%.1f", doublePercentages) + "%"
    }
}