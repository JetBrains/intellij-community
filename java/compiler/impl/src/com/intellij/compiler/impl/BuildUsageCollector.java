// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.impl;

import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventId1;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;

public class BuildUsageCollector extends CounterUsagesCollector {
  private static final EventLogGroup GROUP = new EventLogGroup("build.jps", 1);
  private static final EventId1<Long> REBUILD_COMPLETED = GROUP.registerEvent("rebuild.completed", EventFields.Long("duration_ms"));

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  public static void logRebuildCompleted(long durationMs) {
    REBUILD_COMPLETED.log(durationMs);
  }
}
