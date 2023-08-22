// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl;

import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventId1;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;

public final class ProjectViewPerformanceCollector extends CounterUsagesCollector {

  private static final EventLogGroup GROUP = new EventLogGroup("project.view.performance", 2);

  private static final EventId1<Long> EXPAND_DIR_DURATION = GROUP.registerEvent("dir.expanded", EventFields.DurationMs);

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  public static void logExpandDirDuration(long durationMs) {
    EXPAND_DIR_DURATION.log(durationMs);
  }

}
