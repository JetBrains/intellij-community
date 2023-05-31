// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

class IdeErrorDialogUsageCollector : CounterUsagesCollector() {

  override fun getGroup(): EventLogGroup = GROUP

  companion object {

    private val GROUP = EventLogGroup("ide.error.dialog", 2)
    private val CLEAR_ALL = GROUP.registerEvent("clear.all")
    private val REPORT = GROUP.registerEvent("report")
    private val REPORT_ALL = GROUP.registerEvent("report.all")
    private val REPORT_AND_CLEAR_ALL = GROUP.registerEvent("report.and.clear.all")

    @JvmStatic
    fun logClearAll(): Unit = CLEAR_ALL.log()

    @JvmStatic
    fun logReport(): Unit = REPORT.log()

    @JvmStatic
    fun logReportAll(): Unit = REPORT_ALL.log()

    @JvmStatic
    fun logReportAndClearAll(): Unit = REPORT_AND_CLEAR_ALL.log()
  }
}
