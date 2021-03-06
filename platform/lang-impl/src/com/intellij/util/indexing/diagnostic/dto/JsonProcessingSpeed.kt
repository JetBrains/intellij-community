// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.diagnostic.dto

import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.indexing.diagnostic.BytesNumber
import com.intellij.util.indexing.diagnostic.TimeNano
import java.util.concurrent.TimeUnit

data class JsonProcessingSpeed(val totalBytes: BytesNumber, val totalTime: TimeNano) {
  fun presentableSpeed(): String {
    if (totalTime == 0L) {
      return "0 B/s"
    }
    val bytesPerSecond = (totalBytes.toDouble() * TimeUnit.SECONDS.toNanos(1).toDouble() / totalTime).toLong()
    return StringUtil.formatFileSize(bytesPerSecond) + "/s"
  }
}