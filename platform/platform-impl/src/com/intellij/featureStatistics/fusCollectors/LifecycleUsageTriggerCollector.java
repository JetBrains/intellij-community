// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.featureStatistics.fusCollectors;

import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.eventLog.FeatureUsageGroup;
import com.intellij.internal.statistic.eventLog.FeatureUsageLogger;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class LifecycleUsageTriggerCollector {
  private static final Logger LOG = Logger.getInstance("#com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector");
  private static final FeatureUsageGroup LIFECYCLE = new FeatureUsageGroup("lifecycle", 2);

  public static void onIdeStart() {
    FeatureUsageLogger.INSTANCE.log(LIFECYCLE, "ide.start");
  }

  public static void onIdeClose(boolean restart) {
    final Map<String, Object> data = new FeatureUsageData().addData("restart", restart).build();
    FeatureUsageLogger.INSTANCE.log(LIFECYCLE, "ide.close", data);
  }

  public static void onProjectOpenFinished(@NotNull Project project, long time) {
    final Map<String, Object> data = new FeatureUsageData().
      addProject(project).
      addData("time_ms", time).build();
    FeatureUsageLogger.INSTANCE.log(LIFECYCLE, "project.opening.finished", data);
  }

  public static void onProjectOpened(@NotNull Project project) {
    final Map<String, Object> data = new FeatureUsageData().addProject(project).build();
    FeatureUsageLogger.INSTANCE.log(LIFECYCLE, "project.opened", data);
  }

  public static void onProjectClosed(@NotNull Project project) {
    final Map<String, Object> data = new FeatureUsageData().addProject(project).build();
    FeatureUsageLogger.INSTANCE.log(LIFECYCLE, "project.closed", data);
  }

  public static void onFreeze(int lengthInSeconds) {
    final FeatureUsageData builder =
      new FeatureUsageData().addData("duration_s", lengthInSeconds).addData("duration_grouped", toLengthGroup(lengthInSeconds));
    FeatureUsageLogger.INSTANCE.log(LIFECYCLE, "ide.freeze", builder.build());
  }

  public static void onError(boolean isOOM, boolean isMappingFailed, @Nullable String pluginId) {
    try {
      final FeatureUsageData builder =
        new FeatureUsageData().
          addData("oom", isOOM).
          addData("mapping_failed", isMappingFailed).
          addData("plugin", StringUtil.notNullize(pluginId, "unknown"));
      FeatureUsageLogger.INSTANCE.log(LIFECYCLE, "ide.error", builder.build());
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
