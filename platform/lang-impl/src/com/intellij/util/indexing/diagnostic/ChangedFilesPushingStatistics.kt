// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.diagnostic

import com.intellij.util.indexing.diagnostic.dto.JsonDateTime
import org.jetbrains.annotations.NonNls
import java.time.ZonedDateTime

class ChangedFilesPushingStatistics(val reason: @NonNls String) {
  var startTime: JsonDateTime = JsonDateTime(ZonedDateTime.now())
  var duration: TimeNano = System.nanoTime()
  var isCancelled: Boolean = false

  fun finished(cancelled: Boolean) {
    isCancelled = cancelled
    duration = System.nanoTime() - duration
  }
}
