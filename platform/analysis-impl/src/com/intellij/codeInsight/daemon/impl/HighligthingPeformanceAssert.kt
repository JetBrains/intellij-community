// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.openapi.application.AccessToken
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.concurrent.atomic.AtomicLong

@Internal
class HighlightingPerformanceAssert {

  private val highlightingPassRunning = ThreadLocal<Boolean>()

  fun runPass(): AccessToken {
    val prevValue = highlightingPassRunning.get()
    highlightingPassRunning.set(true)
    return object : AccessToken() {
      override fun finish() {
        if (prevValue != null)
          highlightingPassRunning.set(prevValue)
        else
          highlightingPassRunning.remove()
      }
    }
  }

  private val lastReport = AtomicLong(-1L)
  private val maxReportInterval = 10 * 1000

  fun assertHighlightingPassNotRunning() {
    if (true != highlightingPassRunning.get()) return
    val curTime = System.currentTimeMillis()
    val lastReportVal = lastReport.get()
    if (curTime - lastReportVal < maxReportInterval) return
    if (!lastReport.compareAndSet(lastReportVal, curTime)) return

    Logger.getInstance(HighlightingPerformanceAssert::class.java)
      .error("the expensive method should not be called inside the Highlighting Pass")
  }

}