// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.internal.statistic.collectors.fus.actions.persistence.ToolWindowCollector.ToolWindowUtilValidator;
import com.intellij.internal.statistic.eventLog.events.EventField;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.StringEventField;
import com.intellij.internal.statistic.service.fus.collectors.FeatureUsageCollectorExtension;

import java.util.Collections;
import java.util.List;

import static com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsEventLogGroup.ACTION_FINISHED_EVENT_ID;

public class ToolwindowFusEventFields implements FeatureUsageCollectorExtension {
  public static final StringEventField TOOLWINDOW =
    EventFields.StringValidatedByCustomRule("toolwindow", ToolWindowUtilValidator.class);

  @Override
  public String getGroupId() {
    return "actions";
  }

  @Override
  public String getEventId() {
    return ACTION_FINISHED_EVENT_ID;
  }

  @Override
  public List<EventField> getExtensionFields() {
    return Collections.singletonList(TOOLWINDOW);
  }
}
