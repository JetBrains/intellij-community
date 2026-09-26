// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.impl;

import com.intellij.internal.statistic.IdeActivityDefinition;
import com.intellij.internal.statistic.StructuredIdeActivity;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventField;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventId1;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.openapi.project.Project;

import java.util.List;

public final class BuildUsageCollector extends CounterUsagesCollector {
  private static final EventLogGroup GROUP = new EventLogGroup("build.jps", 4);

  private static final EventField<Boolean> IS_PRE_COMPILE = EventFields.Boolean(
    "pre_compile",
    "True for pre-compile tasks, false for post-compile tasks"
  );

  private static final IdeActivityDefinition COMPILE_TASK_ACTIVITY = GROUP.registerIdeActivity(
    "compile.tasks",
    new EventField[]{ IS_PRE_COMPILE },
    new EventField[]{ IS_PRE_COMPILE }
  );

  private static final EventId1<Long> REBUILD_COMPLETED = GROUP.registerEvent("rebuild.completed", EventFields.DurationMs);
  private static final EventId1<Long> BUILD_COMPLETED = GROUP.registerEvent("build.completed", EventFields.DurationMs);
  private static final EventId1<Long> AUTO_BUILD_COMPLETED = GROUP.registerEvent("autobuild.completed", EventFields.DurationMs);

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  public static StructuredIdeActivity logCompileTasksStarted(Project project, boolean isPreCompile) {
    return COMPILE_TASK_ACTIVITY.started(project, () -> List.of(IS_PRE_COMPILE.with(isPreCompile)));
  }

  public static void logCompileTasksCompleted(StructuredIdeActivity activity, boolean isPreCompile) {
    activity.finished(() -> List.of(IS_PRE_COMPILE.with(isPreCompile)));
  }

  public static void logBuildCompleted(long durationMs, boolean isRebuild, boolean isAutomake) {
    (isAutomake ? AUTO_BUILD_COMPLETED : isRebuild ? REBUILD_COMPLETED : BUILD_COMPLETED).log(durationMs);
  }
}
