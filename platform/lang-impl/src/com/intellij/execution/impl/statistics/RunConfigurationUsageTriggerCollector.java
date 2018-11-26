// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.impl.statistics;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.internal.statistic.service.fus.collectors.FUSProjectUsageTrigger;
import com.intellij.internal.statistic.service.fus.collectors.FUSUsageContext;
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsageTriggerCollector;
import com.intellij.internal.statistic.utils.PluginType;
import com.intellij.internal.statistic.utils.StatisticsUtilKt;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

public class RunConfigurationUsageTriggerCollector extends ProjectUsageTriggerCollector {
  private static final String UNKNOWN = "UNKNOWN";

  @NotNull
  @Override
  public String getGroupId() {
    return "statistics.run.configuration.start";
  }

  public static void trigger(@NotNull Project project, @NotNull ConfigurationFactory factory, @NotNull Executor executor) {
    final String key = AbstractRunConfigurationTypeUsagesCollector.toReportedId(factory);
    if (StringUtil.isNotEmpty(key)) {
      final PluginType type = StatisticsUtilKt.getPluginType(executor.getClass());
      final FUSUsageContext context = FUSUsageContext.create(type.isSafeToReport() ? executor.getId() : UNKNOWN);
      FUSProjectUsageTrigger.getInstance(project).trigger(RunConfigurationUsageTriggerCollector.class, key, context);
    }
  }
}
