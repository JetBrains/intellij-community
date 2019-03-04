// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.featureStatistics.fusCollectors;

import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger;
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LifecycleUsageTriggerCollector {
  private static final Logger LOG = Logger.getInstance("#com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector");
  private static final String LIFECYCLE = "lifecycle";

  public static void onIdeStart() {
    final FeatureUsageData data = new FeatureUsageData().addData("eap", ApplicationManager.getApplication().isEAP());
    addIfTrue(data, "test", StatisticsUploadAssistant.isTestStatisticsEnabled());
    addIfTrue(data, "command_line", ApplicationManager.getApplication().isCommandLine());
    addIfTrue(data, "internal", ApplicationManager.getApplication().isInternal());
    addIfTrue(data, "headless", ApplicationManager.getApplication().isHeadlessEnvironment());
    FUCounterUsageLogger.getInstance().logEvent(LIFECYCLE, "ide.start", data);
  }

  private static void addIfTrue(@NotNull FeatureUsageData data, @NotNull String key, boolean value) {
    if (value) {
      data.addData(key, value);
    }
  }

  public static void onIdeClose(boolean restart) {
    final FeatureUsageData data = new FeatureUsageData().addData("restart", restart);
    FUCounterUsageLogger.getInstance().logEvent(LIFECYCLE, "ide.close", data);
  }

  public static void onProjectOpenFinished(@NotNull Project project, long time) {
    final FeatureUsageData data = new FeatureUsageData().
      addProject(project).
      addData("time_ms", time);
    FUCounterUsageLogger.getInstance().logEvent(LIFECYCLE, "project.opening.finished", data);
  }

  public static void onProjectOpened(@NotNull Project project) {
    final FeatureUsageData data = new FeatureUsageData().addProject(project);
    FUCounterUsageLogger.getInstance().logEvent(LIFECYCLE, "project.opened", data);
  }

  public static void onProjectClosed(@NotNull Project project) {
    final FeatureUsageData data = new FeatureUsageData().addProject(project);
    FUCounterUsageLogger.getInstance().logEvent(LIFECYCLE, "project.closed", data);
  }

  public static void onFreeze(int lengthInSeconds) {
    final FeatureUsageData data =
      new FeatureUsageData().addData("duration_s", lengthInSeconds).addData("duration_grouped", toLengthGroup(lengthInSeconds));
    FUCounterUsageLogger.getInstance().logEvent(LIFECYCLE, "ide.freeze", data);
  }

  public static void onError(boolean isOOM, boolean isMappingFailed, @Nullable String pluginId) {
    try {
      final FeatureUsageData data =
        new FeatureUsageData().
          addData("oom", isOOM).
          addData("mapping_failed", isMappingFailed).
          addData("plugin", StringUtil.notNullize(pluginId, "unknown"));
      FUCounterUsageLogger.getInstance().logEvent(LIFECYCLE, "ide.error", data);
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
