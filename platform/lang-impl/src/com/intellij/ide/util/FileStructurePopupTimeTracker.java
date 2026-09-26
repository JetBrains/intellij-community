// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util;

import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventId1;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import org.jetbrains.annotations.ApiStatus;

import java.util.concurrent.TimeUnit;

@ApiStatus.Internal
final public class FileStructurePopupTimeTracker extends CounterUsagesCollector {
  private static final EventLogGroup GROUP = new EventLogGroup("file.structure.popup", 2);
  private static final EventId1<Long> LIFE = GROUP.registerEvent("popup.disposed", EventFields.DurationMs);
  private static final EventId1<Long> SHOW = GROUP.registerEvent("data.shown", EventFields.DurationMs);
  private static final EventId1<Long> REBUILD = GROUP.registerEvent("data.filled", EventFields.DurationMs);

  @ApiStatus.Internal
  static public void logRebuildTime(long elapsedTimeNanos) {
    long elapsedTimesMs = TimeUnit.NANOSECONDS.toMillis(elapsedTimeNanos);
    REBUILD.log(elapsedTimesMs);
  }

  @ApiStatus.Internal
  static public void logShowTime(long elapsedTimeNanos) {
    long elapsedTimesMs = TimeUnit.NANOSECONDS.toMillis(elapsedTimeNanos);
    SHOW.log(elapsedTimesMs);
  }

  @ApiStatus.Internal
  static public void logPopupLifeTime(long elapsedTimeNanos) {
    long elapsedTimesMs = TimeUnit.NANOSECONDS.toMillis(elapsedTimeNanos);
    LIFE.log(elapsedTimesMs);
  }

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }
}
