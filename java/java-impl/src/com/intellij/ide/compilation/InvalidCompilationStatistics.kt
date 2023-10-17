// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.compilation

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

internal object InvalidCompilationStatistics : CounterUsagesCollector() {
  val GROUP: EventLogGroup = EventLogGroup("idea.project.statistics", 2)

  val INVALID_COMPILATION_FAILURE = GROUP.registerEvent("invalid.compilation.failure", EventFields.Language)

  override fun getGroup(): EventLogGroup {
    return GROUP
  }
}