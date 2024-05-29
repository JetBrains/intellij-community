// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl;

import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventId1;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class ProjectViewPerformanceCollector extends CounterUsagesCollector {

  private static final EventLogGroup GROUP = new EventLogGroup("project.view.performance", 3);

  private static final EventId1<Long> EXPAND_DIR_DURATION = GROUP.registerEvent("dir.expanded", EventFields.DurationMs);
  private static final EventId1<Long> CACHED_STATE_LOAD_DURATION = GROUP.registerEvent("cached.state.loaded", EventFields.DurationMs);
  private static final EventId1<Long> FULL_STATE_LOAD_DURATION = GROUP.registerEvent("full.state.loaded", EventFields.DurationMs);

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  public static void logExpandDirDuration(long durationMs) {
    EXPAND_DIR_DURATION.log(durationMs);
  }

  public static void logCachedStateLoadDuration(long durationMs) {
    CACHED_STATE_LOAD_DURATION.log(durationMs);
  }

  public static void logFullStateLoadDuration(long durationMs) {
    FULL_STATE_LOAD_DURATION.log(durationMs);
  }

}
