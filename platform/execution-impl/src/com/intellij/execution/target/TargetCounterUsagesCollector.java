// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target;

import com.intellij.execution.impl.statistics.RunConfigurationUsageTriggerCollector.RunTargetValidator;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventId1;
import com.intellij.internal.statistic.eventLog.events.EventId2;
import com.intellij.internal.statistic.eventLog.events.StringEventField;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

final class TargetCounterUsagesCollector extends CounterUsagesCollector {
  private static final EventLogGroup GROUP;
  private static final EventId1<String> TARGET_CREATION_BEGAN_EVENT;
  private static final EventId2<String, Integer> TARGET_CREATION_CANCELLED_EVENT;
  private static final EventId1<String> TARGET_CREATION_SUCCEEDED_EVENT;

  static {
    GROUP = new EventLogGroup("run.target.events", 2);
    StringEventField targetTypeField =
      EventFields.StringValidatedByCustomRule("type", RunTargetValidator.class);
    TARGET_CREATION_BEGAN_EVENT = GROUP.registerEvent("creation.began", targetTypeField);
    TARGET_CREATION_CANCELLED_EVENT = GROUP.registerEvent("creation.cancelled", targetTypeField, EventFields.Int("step_number"));
    TARGET_CREATION_SUCCEEDED_EVENT = GROUP.registerEvent("creation.succeeded", targetTypeField);
  }

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  public static void reportTargetCreationBegan(@NotNull Project project,
                                               @NotNull String typeId) {
    TARGET_CREATION_BEGAN_EVENT.log(project, typeId);
  }

  public static void reportTargetCreationCancelled(@NotNull Project project,
                                                   @NotNull String typeId,
                                                   int currentStep) {
    TARGET_CREATION_CANCELLED_EVENT.log(project, typeId, currentStep);
  }

  public static void reportTargetCreationSucceeded(@NotNull Project project,
                                                   @NotNull String typeId) {
    TARGET_CREATION_SUCCEEDED_EVENT.log(project, typeId);
  }
}
