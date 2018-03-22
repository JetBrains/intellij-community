// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.dashboard;

import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector;
import com.intellij.internal.statistic.utils.StatisticsUtilKt;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Konstantin Aleev
 */
public class RunDashboardUsagesCollector extends ProjectUsagesCollector {
  @NotNull
  @Override
  public String getGroupId() {
    return "statistics.run.dashboard";
  }

  @NotNull
  @Override
  public Set<UsageDescriptor> getUsages(@NotNull Project project) {
    final Set<UsageDescriptor> usages = new HashSet<>();
    final Set<String> types = RunDashboardManager.getInstance(project).getTypes();
    usages.add(StatisticsUtilKt.getBooleanUsage("run.dashboard", !types.isEmpty()));
    types.forEach(type -> usages.add(new UsageDescriptor(type)));
    return usages;
  }
}
