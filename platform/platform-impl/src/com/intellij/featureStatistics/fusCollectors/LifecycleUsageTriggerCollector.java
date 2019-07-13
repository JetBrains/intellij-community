// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.featureStatistics.fusCollectors;

import com.intellij.diagnostic.PluginException;
import com.intellij.diagnostic.VMOptions;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.internal.statistic.utils.PluginInfoDetectorKt.getPlatformPlugin;
import static com.intellij.internal.statistic.utils.PluginInfoDetectorKt.getPluginInfoById;

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
      addData("duration_ms", time);
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

  public static void onFrameActivated(@Nullable Project project) {
    final FeatureUsageData data = new FeatureUsageData().addProject(project);
    FUCounterUsageLogger.getInstance().logEvent(LIFECYCLE, "frame.activated", data);
  }

  public static void onFrameDeactivated(@Nullable Project project) {
    final FeatureUsageData data = new FeatureUsageData().addProject(project);
    FUCounterUsageLogger.getInstance().logEvent(LIFECYCLE, "frame.deactivated", data);
  }

  public static void onFreeze(int lengthInSeconds) {
    final long ms = (long)lengthInSeconds * 1000;
    final FeatureUsageData data = new FeatureUsageData().
      addData("duration_ms", ms).
      addData("duration_grouped", toLengthGroup(lengthInSeconds));
    FUCounterUsageLogger.getInstance().logEvent(LIFECYCLE, "ide.freeze", data);
  }

  public static void onError(@Nullable PluginId pluginId,
                             @Nullable Throwable throwable,
                             @Nullable VMOptions.MemoryKind memoryErrorKind) {
    try {
      final FeatureUsageData data = new FeatureUsageData().
        addPluginInfo(pluginId == null ? getPlatformPlugin() : getPluginInfoById(pluginId)).
        addData("error", getThrowableClassName(throwable));

      if (memoryErrorKind != null) {
        data.addData("memory_error_kind", StringUtil.toLowerCase(memoryErrorKind.name()));
      }
      FUCounterUsageLogger.getInstance().logEvent(LIFECYCLE, "ide.error", data);
    }
    catch (Exception e) {
      LOG.warn(e);
    }
  }

  @NotNull
  private static String toLengthGroup(int seconds) {
    if (seconds >= 60) {
      return "60s+";
    }
    if (seconds > 10) {
      seconds -= (seconds % 10);
      return seconds + "s+";
    }
    return seconds + "s";
  }

  @NotNull
  private static String getThrowableClassName(@Nullable Throwable t) {
    if (t == null) {
      return "unknown";
    }

    final boolean isPluginException = t instanceof PluginException && t.getCause() != null;
    final Class throwableClass = isPluginException ? t.getCause().getClass() : t.getClass();

    final PluginInfo throwableLocation = PluginInfoDetectorKt.getPluginInfo(throwableClass);
    return (throwableLocation.isSafeToReport()) ? throwableClass.getName() : "third.party";
  }
}
