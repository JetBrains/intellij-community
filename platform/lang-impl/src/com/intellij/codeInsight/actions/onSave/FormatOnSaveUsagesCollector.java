// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.actions.onSave;

import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventId1;
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

final class FormatOnSaveUsagesCollector extends ProjectUsagesCollector {
  private static final EventLogGroup GROUP = new EventLogGroup("actions.on.save", 1);

  private static final EventId1<Boolean> REFORMAT_CODE_ON_SAVE = GROUP.registerEvent("reformat.code", EventFields.Enabled);
  private static final EventId1<Boolean> OPTIMIZE_IMPORTS_ON_SAVE = GROUP.registerEvent("optimize.imports", EventFields.Enabled);
  private static final EventId1<Boolean> REARRANGE_CODE_ON_SAVE = GROUP.registerEvent("rearrange.code", EventFields.Enabled);
  private static final EventId1<Boolean> CLEANUP_CODE_ON_SAVE = GROUP.registerEvent("cleanup.code", EventFields.Enabled);

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  @Override
  protected @NotNull Set<MetricEvent> getMetrics(@NotNull Project project) {
    return Set.of(
      REFORMAT_CODE_ON_SAVE.metric(FormatOnSaveOptions.getInstance(project).isRunOnSaveEnabled()),
      OPTIMIZE_IMPORTS_ON_SAVE.metric(OptimizeImportsOnSaveOptions.getInstance(project).isRunOnSaveEnabled()),
      REARRANGE_CODE_ON_SAVE.metric(RearrangeCodeOnSaveActionInfo.isRearrangeCodeOnSaveEnabled(project)),
      CLEANUP_CODE_ON_SAVE.metric(CodeCleanupOnSaveActionInfo.isCodeCleanupOnSaveEnabled(project))
    );
  }
}
