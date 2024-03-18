// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventField;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventId;
import com.intellij.internal.statistic.eventLog.events.EventId1;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class JavaCodeVisionUsageCollector extends CounterUsagesCollector {
  public static final String CLASS_LOCATION = "class";
  public static final String METHOD_LOCATION = "method";

  private static final EventLogGroup ourGroup = new EventLogGroup("java.lens", 4);

  private static final EventField<String> LOCATION_FIELD = EventFields.String("location", List.of(CLASS_LOCATION, METHOD_LOCATION));

  static final EventId USAGES_CLICKED_EVENT_ID = ourGroup.registerEvent("usages.clicked");
  static final EventId1<String> IMPLEMENTATION_CLICKED_EVENT_ID = ourGroup.registerEvent("implementations.clicked", LOCATION_FIELD);
  static final EventId SETTINGS_CLICKED_EVENT_ID = ourGroup.registerEvent("setting.clicked");
  public static final EventId RELATED_PROBLEMS_CLICKED_EVENT_ID = ourGroup.registerEvent("related.problems.clicked");
  private static final EventId1<String> CODE_AUTHOR_CLICKED_EVENT = ourGroup.registerEvent("code.author.clicked", LOCATION_FIELD);

  @Override
  public EventLogGroup getGroup() {
    return ourGroup;
  }

  public static void logCodeAuthorClicked(@Nullable Project project, @NotNull String location) {
    CODE_AUTHOR_CLICKED_EVENT.log(project, location);
  }
}
