// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventId;
import com.intellij.internal.statistic.eventLog.events.EventId1;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;

import java.util.List;

public class JavaCodeVisionUsageCollector extends CounterUsagesCollector {
   private static final EventLogGroup ourGroup = new EventLogGroup("java.lens", 3);
   static final EventId USAGES_CLICKED_EVENT_ID = ourGroup.registerEvent("usages.clicked");
   static final EventId1<String> IMPLEMENTATION_CLICKED_EVENT_ID = ourGroup.registerEvent(
    "implementations.clicked",
    EventFields.String("location", List.of("class", "method"))
  );
   static final EventId SETTINGS_CLICKED_EVENT_ID = ourGroup.registerEvent("setting.clicked");
   public static final EventId RELATED_PROBLEMS_CLICKED_EVENT_ID = ourGroup.registerEvent("related.problems.clicked");

  @Override
  public EventLogGroup getGroup() {
    return ourGroup;
  }
}
