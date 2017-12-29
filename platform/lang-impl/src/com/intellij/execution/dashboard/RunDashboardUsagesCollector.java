// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.dashboard;

import com.intellij.internal.statistic.AbstractProjectsUsagesCollector;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.utils.StatisticsUtilKt;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author Konstantin Aleev
 */
public class RunDashboardUsagesCollector extends AbstractProjectsUsagesCollector {
  @NotNull
  @Override
  public GroupDescriptor getGroupId() {
    return GroupDescriptor.create("Run Dashboard");
  }

  @NotNull
  @Override
  public Set<UsageDescriptor> getProjectUsages(@NotNull Project project) {
    final Set<UsageDescriptor> usages = new HashSet<>();
    final Set<String> types = RunDashboardManager.getInstance(project).getTypes();
    usages.add(StatisticsUtilKt.getBooleanUsage("run.dashboard", !types.isEmpty()));
    types.forEach(type -> usages.add(new UsageDescriptor(type)));
    return usages;
  }
}
