// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.colors

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields

object ReaderModeStatsCollector {
  val SEE_ALSO_EVENT = EventLogGroup("reader.mode", 1).registerEvent("see.also.navigation", EventFields.Count)
}