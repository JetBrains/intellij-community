// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.impl.statistics;

import com.intellij.internal.statistic.eventLog.EventField;
import com.intellij.internal.statistic.eventLog.EventFields;
import com.intellij.internal.statistic.service.fus.collectors.FeatureUsageCollectorExtension;

import java.util.Collections;
import java.util.List;

public class RunConfigurationTypeLanguageExtension implements FeatureUsageCollectorExtension {
  @Override
  public String getGroupId() {
    return RunConfigurationTypeUsagesCollector.GROUP.getId();
  }

  @Override
  public String getEventId() {
    return RunConfigurationTypeUsagesCollector.CONFIGURED_IN_PROJECT;
  }

  @Override
  public List<EventField> getExtensionFields() {
    return Collections.singletonList(EventFields.Language);
  }
}
