// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.featureStatistics.fusCollectors;

import com.intellij.diagnostic.VMOptions;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger;
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.internal.statistic.utils.PluginInfoDetectorKt.getPlatformPlugin;
import static com.intellij.internal.statistic.utils.PluginInfoDetectorKt.getPluginInfoById;

public final class LifecycleUsageTriggerCollector {
  private static final Logger LOG = Logger.getInstance(LifecycleUsageTriggerCollector.class);
  private static final String LIFECYCLE = "lifecycle";

  private static final EventsRateThrottle ourErrorsRateThrottle = new EventsRateThrottle(100, 5L * 60 * 1000); // 100 errors per 5 minutes
  private static final EventsIdentityThrottle ourErrorsIdentityThrottle = new EventsIdentityThrottle(50, 60L * 60 * 1000); // 1 unique error per 1 hour

  public static void onIdeStart() {
    Application app = ApplicationManager.getApplication();
    FeatureUsageData data = new FeatureUsageData().addData("eap", app.isEAP());
    addIfTrue(data, "test", StatisticsUploadAssistant.isTestStatisticsEnabled());
    addIfTrue(data, "command_line", app.isCommandLine());
    addIfTrue(data, "internal", app.isInternal());
    addIfTrue(data, "headless", app.isHeadlessEnvironment());
    FUCounterUsageLogger.getInstance().logEvent(LIFECYCLE, "ide.start", data);
  }

  private static void addIfTrue(@NotNull FeatureUsageData data, @NotNull String key, boolean value) {
    if (value) {
      data.addData(key, true);
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

  public static void onFreeze(long durationMs) {
    final FeatureUsageData data = new FeatureUsageData().
      addData("duration_ms", durationMs).
      addData("duration_grouped", toLengthGroup((int)(durationMs / 1000)));
    FUCounterUsageLogger.getInstance().logEvent(LIFECYCLE, "ide.freeze", data);
  }

  public static void onError(@Nullable PluginId pluginId,
                             @Nullable Throwable throwable,
                             @Nullable VMOptions.MemoryKind memoryErrorKind) {
    try {
      final ThrowableDescription description = new ThrowableDescription(throwable);

      final FeatureUsageData data = new FeatureUsageData().
        addPluginInfo(pluginId == null ? getPlatformPlugin() : getPluginInfoById(pluginId)).
        addData("error", description.getClassName());

      if (memoryErrorKind != null) {
        data.addData("memory_error_kind", StringUtil.toLowerCase(memoryErrorKind.name()));
      }

      if (ourErrorsRateThrottle.tryPass(System.currentTimeMillis())) {

        List<String> frames = description.getLastFrames(50);
        int framesHash = frames.hashCode();

        data.addData("error_hash", framesHash);

        if (ourErrorsIdentityThrottle.tryPass(framesHash, System.currentTimeMillis())) {
          data.
            addData("error_frames", frames).
            addData("error_size", description.getSize());
        }
      }
      else {
        data.addData("too_many_errors", true);
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
}
