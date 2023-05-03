// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventId1;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class WindowsDefenderStatisticsCollector extends CounterUsagesCollector {
  private static final EventLogGroup GROUP = new EventLogGroup("defender", 1);

  private enum Status {Skipped, Enabled, Disabled, Error}
  private enum Reaction {Auto, Manual, ProjectMute, GlobalMute}

  private static final EventId1<Status> PROTECTION_CHECK_EVENT = GROUP.registerEvent("protection", EventFields.Enum("status", Status.class));
  private static final EventId1<Reaction> NOTIFICATION_EVENT = GROUP.registerEvent("notification", EventFields.Enum("reaction", Reaction.class));
  private static final EventId1<Boolean> AUTO_CONFIG_EVENT = GROUP.registerEvent("auto_config", EventFields.Boolean("success"));

  static void protectionCheckSkipped(@NotNull Project project) {
    PROTECTION_CHECK_EVENT.log(project, Status.Skipped);
  }

  static void protectionCheckStatus(@NotNull Project project, @Nullable Boolean status) {
    PROTECTION_CHECK_EVENT.log(project, status == Boolean.TRUE ? Status.Enabled : status == Boolean.FALSE ? Status.Disabled : Status.Error);
  }

  static void auto(@NotNull Project project) {
    NOTIFICATION_EVENT.log(project, Reaction.Auto);
  }

  static void manual(@NotNull Project project) {
    NOTIFICATION_EVENT.log(project, Reaction.Manual);
  }

  static void suppressed(@NotNull Project project, boolean globally) {
    NOTIFICATION_EVENT.log(project, globally ? Reaction.GlobalMute : Reaction.ProjectMute);
  }

  static void configured(@NotNull Project project, boolean success) {
    AUTO_CONFIG_EVENT.log(project, success);
  }

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }
}
