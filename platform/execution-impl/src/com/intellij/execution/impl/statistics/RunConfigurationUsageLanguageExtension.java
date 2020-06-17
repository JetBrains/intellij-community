// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.impl.statistics;

import com.intellij.internal.statistic.IdeActivity;
import com.intellij.internal.statistic.eventLog.EventField;
import com.intellij.internal.statistic.eventLog.EventFields;
import com.intellij.internal.statistic.service.fus.collectors.FeatureUsageCollectorExtension;

import java.util.Collections;
import java.util.List;

public class RunConfigurationUsageLanguageExtension implements FeatureUsageCollectorExtension {
  @Override
  public String getGroupId() {
    return RunConfigurationUsageTriggerCollector.GROUP;
  }

  @Override
  public String getEventId() {
    return IdeActivity.STARTED_EVENT_ID;
  }

  @Override
  public List<EventField> getExtensionFields() {
    return Collections.singletonList(EventFields.Language);
  }
}
