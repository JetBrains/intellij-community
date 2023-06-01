// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.VarargEventId;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;

/**
 * Report time between Alt-Enter invocation and the intention popup appearing onscreen
 */
public class ShowIntentionActionFUSCollector extends CounterUsagesCollector {
  private static final EventLogGroup GROUP = new EventLogGroup("show.intention.action.popup", 1);

  static final VarargEventId SHOWN = GROUP.registerVarargEvent("shown", EventFields.DurationMs, EventFields.FileType);

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }
}
