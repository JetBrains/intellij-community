// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.colors

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object ReaderModeStatsCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  private val GROUP = EventLogGroup("reader.mode", 2)
  private val seeAlsoNavigation = GROUP.registerEvent("see.also.navigation")
  private val switchedEvent = GROUP.registerEvent("widget.switched", EventFields.Enabled)

  @JvmStatic
  fun logSeeAlsoNavigation() {
    seeAlsoNavigation.log()
  }

  fun readerModeSwitched(enabled: Boolean) {
    switchedEvent.log(enabled)
  }
}