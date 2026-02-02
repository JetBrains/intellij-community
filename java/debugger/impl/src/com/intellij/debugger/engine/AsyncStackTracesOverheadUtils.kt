// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.intellij.debugger.statistics.DebuggerStatistics
import com.intellij.openapi.util.Key
import kotlinx.coroutines.FlowPreview
import java.util.concurrent.TimeUnit

private val sessionStartTimestampKey = Key.create<Long>("debuggerSessionStartTimestamp")

internal fun initializeOverheadListener(process: DebugProcessImpl) {
  val startNs = System.nanoTime()
  process.putUserData(sessionStartTimestampKey, startNs)
}

@OptIn(FlowPreview::class)
internal fun onOverheadDetected(process: DebugProcessImpl) {
  val project = process.project
  val sessionStartNs = process.getUserData(sessionStartTimestampKey)
  val sessionLengthMs = if (sessionStartNs != null) {
    val durationNs = System.nanoTime() - sessionStartNs
    TimeUnit.NANOSECONDS.toMillis(durationNs)
  }
  else {
    -1
  }
  DebuggerStatistics.logAgentOverheadDetected(project, sessionLengthMs)
}
