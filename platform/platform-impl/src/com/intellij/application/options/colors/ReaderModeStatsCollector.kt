// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.colors

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

class ReaderModeStatsCollector : CounterUsagesCollector() {
  override fun getGroup() = GROUP

  companion object {
    private val GROUP = EventLogGroup("reader.mode", 1)
    private val seeAlsoNavigation = GROUP.registerEvent("see.also.navigation")

    @JvmStatic
    fun logSeeAlsoNavigation() = seeAlsoNavigation.log()
  }
}