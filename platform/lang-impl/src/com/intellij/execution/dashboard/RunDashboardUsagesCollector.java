// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.dashboard;

import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.impl.statistics.RunConfigurationTypeUsagesCollector;
import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventId1;
import com.intellij.internal.statistic.eventLog.events.EventPair;
import com.intellij.internal.statistic.eventLog.events.VarargEventId;
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule;
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class RunDashboardUsagesCollector extends ProjectUsagesCollector {
  public static final EventLogGroup GROUP = new EventLogGroup("run.dashboard", 5);
  public static final EventId1<Boolean> RUN_DASHBOARD = GROUP.registerEvent("run.dashboard", EventFields.Boolean("enabled"));
  public static final VarargEventId ADDED_RUN_CONFIGURATION = GROUP.registerVarargEvent("added.run.configuration",
                                                                                        RunConfigurationTypeUsagesCollector.ID_FIELD);
  public static final VarargEventId REMOVED_RUN_CONFIGURATION = GROUP.registerVarargEvent("removed.run.configuration",
                                                                                          RunConfigurationTypeUsagesCollector.ID_FIELD);

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  @NotNull
  @Override
  public Set<MetricEvent> getMetrics(@NotNull Project project) {
    final Set<MetricEvent> metricEvents = new HashSet<>();
    RunDashboardManagerImpl runDashboardManager = (RunDashboardManagerImpl)RunDashboardManager.getInstance(project);
    final Set<String> dashboardTypes = new HashSet<>(runDashboardManager.getTypes());
    Set<String> removedDefaultType = new HashSet<>(runDashboardManager.getEnableByDefaultTypes());
    dashboardTypes.removeAll(removedDefaultType); // do not report enable by default types
    removedDefaultType.removeAll(runDashboardManager.getTypes());
    metricEvents.add(RUN_DASHBOARD.metric(!dashboardTypes.isEmpty()));

    if (!dashboardTypes.isEmpty()) {
      List<ConfigurationType> configurationTypes = ConfigurationType.CONFIGURATION_TYPE_EP.getExtensionList();
      for (String dashboardType : dashboardTypes) {
        ConfigurationType configurationType = ContainerUtil.find(configurationTypes, type -> type.getId().equals(dashboardType));
        if (configurationType == null) continue;

        List<EventPair<?>> data = RunConfigurationTypeUsagesCollector.createFeatureUsageData(configurationType, null);
        metricEvents.add(ADDED_RUN_CONFIGURATION.metric(data));
      }
    }
    if (!removedDefaultType.isEmpty()) {
      List<ConfigurationType> configurationTypes = ConfigurationType.CONFIGURATION_TYPE_EP.getExtensionList();
      for (String removedType : removedDefaultType) {
        ConfigurationType configurationType = ContainerUtil.find(configurationTypes, type -> type.getId().equals(removedType));
        if (configurationType == null) continue;

        List<EventPair<?>> data = RunConfigurationTypeUsagesCollector.createFeatureUsageData(configurationType, null);
        metricEvents.add(REMOVED_RUN_CONFIGURATION.metric(data));
      }
    }
    return metricEvents;
  }


  public static class RunConfigurationTypeValidator extends CustomValidationRule {
    @NotNull
    @Override
    public String getRuleId() {
      return "run_config";
    }

    @NotNull
    @Override
    protected ValidationResultType doValidate(@NotNull String data, @NotNull EventContext context) {
      if (isThirdPartyValue(data)) return ValidationResultType.ACCEPTED;
      ConfigurationType configurationType = findConfigurationTypeById(data);
      if (configurationType == null) return ValidationResultType.REJECTED;
      return PluginInfoDetectorKt.getPluginInfo(configurationType.getClass()).isDevelopedByJetBrains() ? ValidationResultType.ACCEPTED : ValidationResultType.THIRD_PARTY;
    }

    private static ConfigurationType findConfigurationTypeById(@NotNull String data) {
      return ContainerUtil.find(ConfigurationType.CONFIGURATION_TYPE_EP.getExtensionList(), type -> type.getId().equals(data));
    }
  }
}
