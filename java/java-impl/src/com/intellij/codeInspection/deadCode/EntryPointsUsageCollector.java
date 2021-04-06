// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.deadCode;

import com.intellij.codeInspection.ex.EntryPointsManagerBase;
import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventId1;
import com.intellij.internal.statistic.eventLog.events.EventId2;
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.Set;

public class EntryPointsUsageCollector extends ProjectUsagesCollector {
  private static final EventLogGroup GROUP = new EventLogGroup("entry.points", 1);
  private static final EventId2<Boolean, Boolean> ANNOTATIONS =
    GROUP.registerEvent("annotations",
                        EventFields.Boolean("additional_used"),
                        EventFields.Boolean("write_used"));
  private static final EventId1<Boolean> PATTERNS =
    GROUP.registerEvent("patterns",
                        EventFields.Boolean("used"));

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  @Override
  protected @NotNull Set<MetricEvent> getMetrics(@NotNull Project project) {
    EntryPointsManagerBase entryPointManager = EntryPointsManagerBase.getInstance(project);
    boolean additionalAnnotationsUsed = !entryPointManager.getAdditionalAnnotations().isEmpty();
    boolean writeAnnotationsUsed = !entryPointManager.getWriteAnnotations().isEmpty();
    boolean patternsUsed = !entryPointManager.getPatterns().isEmpty();

    Set<MetricEvent> result = new LinkedHashSet<>();
    result.add(ANNOTATIONS.metric(additionalAnnotationsUsed, writeAnnotationsUsed));
    result.add(PATTERNS.metric(patternsUsed));
    return result;
  }
}
