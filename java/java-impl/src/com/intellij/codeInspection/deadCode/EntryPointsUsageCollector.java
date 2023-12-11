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
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class EntryPointsUsageCollector extends ProjectUsagesCollector {
  private static final EventLogGroup GROUP = new EventLogGroup("entry.points", 2);
  private static final EventId2<Boolean, Boolean> ADDITIONAL_ANNOTATIONS =
    GROUP.registerEvent("additional_annotations",
                        EventFields.Boolean("fqn_used"),
                        EventFields.Boolean("patterns_used"));
  private static final EventId1<Boolean> WRITE_ANNOTATIONS =
    GROUP.registerEvent("write_annotations",
                        EventFields.Boolean("used"));
  private static final EventId1<Boolean> CLASS_PATTERNS =
    GROUP.registerEvent("class_patterns",
                        EventFields.Boolean("used"));

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  @Override
  protected @NotNull Set<MetricEvent> getMetrics(@NotNull Project project) {
    EntryPointsManagerBase entryPointManager = EntryPointsManagerBase.getInstance(project);
    Set<MetricEvent> result = new LinkedHashSet<>();
    addAdditionalAnnotationsMetric(result, entryPointManager.getCustomAdditionalAnnotations());
    result.add(WRITE_ANNOTATIONS.metric(!entryPointManager.getWriteAnnotations().isEmpty()));
    result.add(CLASS_PATTERNS.metric(!entryPointManager.getPatterns().isEmpty()));
    return result;
  }

  private static void addAdditionalAnnotationsMetric(@NotNull Set<MetricEvent> metrics, @NotNull List<String> annotations) {
    if (annotations.isEmpty()) {
      metrics.add(ADDITIONAL_ANNOTATIONS.metric(false, false));
      return;
    }
    int patternsAmount = ContainerUtil.count(annotations, fqn -> fqn.endsWith("*"));
    int fqnAmount = annotations.size() - patternsAmount;
    MetricEvent metric = ADDITIONAL_ANNOTATIONS.metric(fqnAmount > 0, patternsAmount > 0);
    metrics.add(metric);
  }
}
