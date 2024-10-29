// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl.statistics;

import com.intellij.internal.statistic.eventLog.events.EventField;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.service.fus.collectors.FeatureUsageCollectorExtension;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;

@ApiStatus.Internal
public final class RunConfigurationUsageLanguageExtension implements FeatureUsageCollectorExtension {
  @Override
  public String getGroupId() {
    return RunConfigurationUsageTriggerCollector.GROUP_NAME;
  }

  @Override
  public String getEventId() {
    return "started";
  }

  @Override
  public List<EventField> getExtensionFields() {
    return List.of(EventFields.Language, RunConfigurationUsageTriggerCollector.ALTERNATIVE_JRE_VERSION);
  }
}
