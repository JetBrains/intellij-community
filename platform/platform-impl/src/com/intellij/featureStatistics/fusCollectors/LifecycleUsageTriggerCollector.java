// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.featureStatistics.fusCollectors;

import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.eventLog.FeatureUsageGroup;
import com.intellij.internal.statistic.service.fus.collectors.FUSCounterUsageLogger;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LifecycleUsageTriggerCollector {
  private static final Logger LOG = Logger.getInstance("#com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector");
  private static final FeatureUsageGroup LIFECYCLE = new FeatureUsageGroup("lifecycle", 2);

  public static void onIdeStart() {
    FUSCounterUsageLogger.logEvent(LIFECYCLE, "ide.start");
  }

  public static void onIdeClose(boolean restart) {
    final FeatureUsageData data = new FeatureUsageData().addData("restart", restart);
    FUSCounterUsageLogger.logEvent(LIFECYCLE, "ide.close", data);
  }

  public static void onProjectOpenFinished(@NotNull Project project, long time) {
    final FeatureUsageData data = new FeatureUsageData().
      addProject(project).
      addData("time_ms", time);
    FUSCounterUsageLogger.logEvent(LIFECYCLE, "project.opening.finished", data);
  }

  public static void onProjectOpened(@NotNull Project project) {
    final FeatureUsageData data = new FeatureUsageData().addProject(project);
    FUSCounterUsageLogger.logEvent(LIFECYCLE, "project.opened", data);
  }

  public static void onProjectClosed(@NotNull Project project) {
    final FeatureUsageData data = new FeatureUsageData().addProject(project);
    FUSCounterUsageLogger.logEvent(LIFECYCLE, "project.closed", data);
  }

  public static void onFreeze(int lengthInSeconds) {
    final FeatureUsageData data =
      new FeatureUsageData().addData("duration_s", lengthInSeconds).addData("duration_grouped", toLengthGroup(lengthInSeconds));
    FUSCounterUsageLogger.logEvent(LIFECYCLE, "ide.freeze", data);
  }

  public static void onError(boolean isOOM, boolean isMappingFailed, @Nullable String pluginId) {
    try {
      final FeatureUsageData data =
        new FeatureUsageData().
          addData("oom", isOOM).
          addData("mapping_failed", isMappingFailed).
          addData("plugin", StringUtil.notNullize(pluginId, "unknown"));
      FUSCounterUsageLogger.logEvent(LIFECYCLE, "ide.error", data);
    }
    catch (Exception e) {
      LOG.warn(e);
    }
  }

  @NotNull
  private static String toLengthGroup(int seconds) {
    if (seconds >= 60) {
      return "60+";
    }
    if (seconds > 10) {
      seconds -= (seconds % 10);
      return seconds + "+";
    }
    return String.valueOf(seconds);
  }
}
