// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.actions;

import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventId;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;

public class JavaDebuggerActionsCollector extends CounterUsagesCollector {
  private static final EventLogGroup group = new EventLogGroup("java.debugger.actions", 1);
  public static final EventId attachFromConsoleInlay = group.registerEvent("attach.inlay");
  public static final EventId createExceptionBreakpointInlay = group.registerEvent("create.exception.breakpoint.inlay");

  @Override
  public EventLogGroup getGroup() {
    return group;
  }
}
