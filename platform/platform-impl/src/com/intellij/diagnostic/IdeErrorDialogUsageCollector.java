// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventId;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;

final class IdeErrorDialogUsageCollector extends CounterUsagesCollector {
  private static final EventLogGroup GROUP = new EventLogGroup("ide.error.dialog", 2);

  private static final EventId CLEAR_ALL = GROUP.registerEvent("clear.all");
  private static final EventId REPORT = GROUP.registerEvent("report");
  private static final EventId REPORT_ALL = GROUP.registerEvent("report.all");
  private static final EventId REPORT_AND_CLEAR_ALL = GROUP.registerEvent("report.and.clear.all");

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  static void logClearAll() {
    CLEAR_ALL.log();
  }

  static void logReport() {
    REPORT.log();
  }

  static void logReportAll() {
    REPORT_ALL.log();
  }

  static void logReportAndClearAll() {
    REPORT_AND_CLEAR_ALL.log();
  }
}
