// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.internal.statistic.eventLog.EventField;
import com.intellij.internal.statistic.eventLog.EventFields;
import com.intellij.internal.statistic.eventLog.StringEventField;
import com.intellij.internal.statistic.service.fus.collectors.FeatureUsageCollectorExtension;

import java.util.Collections;
import java.util.List;

public class ToolwindowFusEventFields implements FeatureUsageCollectorExtension {
  public static final StringEventField TOOLWINDOW = EventFields.String("toolwindow").withCustomRule("toolwindow");

  @Override
  public String getGroupId() {
    return "actions";
  }

  @Override
  public String getEventId() {
    return "action.invoked";
  }

  @Override
  public List<EventField> getExtensionFields() {
    return Collections.singletonList(TOOLWINDOW);
  }
}
