// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.dashboard;

import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.impl.statistics.RunConfigurationTypeUsagesCollector;
import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.beans.MetricEventFactoryKt;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomWhiteListRule;
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Konstantin Aleev
 */
public class RunDashboardUsagesCollector extends ProjectUsagesCollector {
  @NotNull
  @Override
  public String getGroupId() {
    return "run.dashboard";
  }

  @Override
  public int getVersion() {
    return 2;
  }

  @NotNull
  @Override
  public Set<MetricEvent> getMetrics(@NotNull Project project) {
    final Set<MetricEvent> metricEvents = new HashSet<>();
    RunDashboardManagerImpl runDashboardManager = (RunDashboardManagerImpl)RunDashboardManager.getInstance(project);
    final Set<String> dashboardTypes = new THashSet<>(runDashboardManager.getTypes());
    dashboardTypes.removeAll(runDashboardManager.getEnableByDefaultTypes()); // do not report enable by default types
    metricEvents.add(MetricEventFactoryKt.newBooleanMetric("run.dashboard", !dashboardTypes.isEmpty()));

    if (!dashboardTypes.isEmpty()) {
      List<ConfigurationType> configurationTypes = ConfigurationType.CONFIGURATION_TYPE_EP.getExtensionList();
      for (String dashboardType : dashboardTypes) {
        ConfigurationType configurationType = ContainerUtil.find(configurationTypes, type -> type.getId().equals(dashboardType));
        if (configurationType == null) continue;

        final FeatureUsageData data = RunConfigurationTypeUsagesCollector.newFeatureUsageData(configurationType, null);
        metricEvents.add(MetricEventFactoryKt.newMetric("added.run.configuration", data));
      }
    }
    return metricEvents;
  }


  public static class RunConfigurationTypeValidator extends CustomWhiteListRule {
    @Override
    public boolean acceptRuleId(@Nullable String ruleId) {
      return "run_config".equals(ruleId);
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
