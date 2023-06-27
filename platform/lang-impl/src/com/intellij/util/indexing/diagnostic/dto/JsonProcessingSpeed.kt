// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.diagnostic.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.indexing.diagnostic.BytesNumber
import com.intellij.util.indexing.diagnostic.TimeNano
import java.util.concurrent.TimeUnit

@JsonIgnoreProperties(ignoreUnknown = true)
data class JsonProcessingSpeed(val totalBytes: BytesNumber = 0, val totalCpuTime: TimeNano = 0) {
  fun presentableSpeed(): String {
    if (totalCpuTime == 0L) {
      return "0 B/s"
    }
    val bytesPerSecond = (totalBytes.toDouble() * TimeUnit.SECONDS.toNanos(1).toDouble() / totalCpuTime).toLong()
    return StringUtil.formatFileSize(bytesPerSecond) + "/s"
  }

  fun toKiloBitsPerSecond(): Int {
    return ((totalBytes.toDouble() * 0.001 / totalCpuTime) * TimeUnit.SECONDS.toNanos(1).toDouble()).toInt()
  }
}