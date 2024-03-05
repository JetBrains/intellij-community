// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.impl;

import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventId1;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;

public final class BuildUsageCollector extends CounterUsagesCollector {
  private static final EventLogGroup GROUP = new EventLogGroup("build.jps", 3);

  private static final EventId1<Long> PRE_COMPILE_BEFORE_TASKS_COMPLETED =
    GROUP.registerEvent("precompile.before.tasks.completed", EventFields.DurationMs);
  private static final EventId1<Long> PRE_COMPILE_AFTER_TASKS_COMPLETED =
    GROUP.registerEvent("precompile.after.tasks.completed", EventFields.DurationMs);

  private static final EventId1<Long> REBUILD_COMPLETED = GROUP.registerEvent("rebuild.completed", EventFields.DurationMs);
  private static final EventId1<Long> BUILD_COMPLETED = GROUP.registerEvent("build.completed", EventFields.DurationMs);
  private static final EventId1<Long> AUTO_BUILD_COMPLETED = GROUP.registerEvent("autobuild.completed", EventFields.DurationMs);

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  public static void logPreCompileCompleted(long durationMs, boolean beforeTasks) {
    (beforeTasks ? PRE_COMPILE_BEFORE_TASKS_COMPLETED : PRE_COMPILE_AFTER_TASKS_COMPLETED).log(durationMs);
  }

  public static void logBuildCompleted(long durationMs, boolean isRebuild, boolean isAutomake) {
    (isAutomake ? AUTO_BUILD_COMPLETED : isRebuild ? REBUILD_COMPLETED : BUILD_COMPLETED).log(durationMs);
  }
}
