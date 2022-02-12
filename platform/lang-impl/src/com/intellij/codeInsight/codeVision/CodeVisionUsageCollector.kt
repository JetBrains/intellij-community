// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.codeVision

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId1
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.lang.Language

class CodeVisionUsageCollector : CounterUsagesCollector() {
  companion object {
    private val ourGroup = EventLogGroup("code.lens", 1)
    val VCS_CODE_AUTHOR_CLICKED: EventId1<Language> = ourGroup.registerEvent("vcs.code.author.clicked", EventFields.Language)
  }

  override fun getGroup(): EventLogGroup {
    return ourGroup
  }
}