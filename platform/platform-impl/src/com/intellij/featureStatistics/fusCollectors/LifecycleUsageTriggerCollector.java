// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.featureStatistics.fusCollectors;

import com.intellij.diagnostic.VMOptions;
import com.intellij.ide.GeneralSettings;
import com.intellij.internal.DebugAttachDetector;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.eventLog.events.*;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.intellij.internal.statistic.utils.PluginInfoDetectorKt.getPlatformPlugin;
import static com.intellij.internal.statistic.utils.PluginInfoDetectorKt.getPluginInfoById;

public final class LifecycleUsageTriggerCollector extends CounterUsagesCollector {
  private static final Logger LOG = Logger.getInstance(LifecycleUsageTriggerCollector.class);
  private static final EventLogGroup LIFECYCLE = new EventLogGroup("lifecycle", 61);

  private static final EventField<Boolean> eapField = EventFields.Boolean("eap");
  private static final EventField<Boolean> testField = EventFields.Boolean("test");
  private static final EventField<Boolean> commandLineField = EventFields.Boolean("command_line");
  private static final EventField<Boolean> internalField = EventFields.Boolean("internal");
  private static final EventField<Boolean> headlessField = EventFields.Boolean("headless");
  private static final EventField<Boolean> debugAgentField = EventFields.Boolean("debug_agent");

  private static final VarargEventId IDE_EVENT_START = LIFECYCLE.registerVarargEvent("ide.start", eapField, testField, commandLineField,
                                                                                     internalField, headlessField, debugAgentField);
  private static final EventId1<Boolean> IDE_CLOSE = LIFECYCLE.registerEvent("ide.close", EventFields.Boolean("restart"));
  private static final EventId1<Long> PROJECT_OPENING_FINISHED =
    LIFECYCLE.registerEvent("project.opening.finished", EventFields.Long("duration_ms"));
  private static final EventId PROJECT_OPENED = LIFECYCLE.registerEvent("project.opened");
  private static final EventId PROJECT_CLOSED = LIFECYCLE.registerEvent("project.closed");
  private static final EventId PROJECT_MODULE_ATTACHED = LIFECYCLE.registerEvent("project.module.attached");
  private static final EventId PROTOCOL_OPEN_COMMAND_HANDLED = LIFECYCLE.registerEvent("protocol.open.command.handled");
  private static final EventId FRAME_ACTIVATED = LIFECYCLE.registerEvent("frame.activated");
  private static final EventId FRAME_DEACTIVATED = LIFECYCLE.registerEvent("frame.deactivated");
  private static final EventField<String> DURATION_GROUPED = new DurationEventField();
  private static final EventId2<Long, String> IDE_FREEZE =
    LIFECYCLE.registerEvent("ide.freeze", EventFields.Long("duration_ms"), DURATION_GROUPED);

  private static final EventField<String> errorField = EventFields.StringValidatedByCustomRule("error", "class_name");
  private static final EventField<VMOptions.MemoryKind> memoryErrorKindField =
    EventFields.Enum("memory_error_kind", VMOptions.MemoryKind.class, (kind) -> StringUtil.toLowerCase(kind.name()));
  private static final EventField<Integer> errorHashField = EventFields.Int("error_hash");
  private static final StringListEventField errorFramesField = EventFields.StringListValidatedByCustomRule("error_frames", "method_name");
  private static final EventField<Integer> errorSizeField = EventFields.Int("error_size");
  private static final EventField<Boolean> tooManyErrorsField = EventFields.Boolean("too_many_errors");
  private static final VarargEventId IDE_ERROR = LIFECYCLE.registerVarargEvent("ide.error",
                                                                               EventFields.PluginInfo,
                                                                               errorField,
                                                                               memoryErrorKindField,
                                                                               errorHashField,
                                                                               errorFramesField,
                                                                               errorSizeField,
                                                                               tooManyErrorsField);
  private static final EventId IDE_CRASH_DETECTED = LIFECYCLE.registerEvent("ide.crash.detected");

  private enum ProjectOpenMode { New, Same, Attach }
  private static final EventField<ProjectOpenMode> projectOpenModeField = EventFields.Enum("mode", ProjectOpenMode.class, (mode) -> StringUtil.toLowerCase(mode.name()));
  private static final EventId1<ProjectOpenMode> PROJECT_FRAME_SELECTED = LIFECYCLE.registerEvent("project.frame.selected", projectOpenModeField);

  private static final EventsRateThrottle ourErrorsRateThrottle = new EventsRateThrottle(100, 5L * 60 * 1000); // 100 errors per 5 minutes
  private static final EventsIdentityThrottle ourErrorsIdentityThrottle = new EventsIdentityThrottle(50, 60L * 60 * 1000); // 1 unique error per 1 hour

  @Override
  public EventLogGroup getGroup() {
    return LIFECYCLE;
  }

  public static void onIdeStart() {
    Application app = ApplicationManager.getApplication();
    IDE_EVENT_START.log(
      eapField.with(app.isEAP()),
      testField.with(StatisticsUploadAssistant.isTestStatisticsEnabled()),
      commandLineField.with(app.isCommandLine()),
      internalField.with(app.isInternal()),
      headlessField.with(app.isHeadlessEnvironment()),
      debugAgentField.with(DebugAttachDetector.isDebugEnabled()));
  }

  public static void onIdeClose(boolean restart) {
    IDE_CLOSE.log(restart);
  }

  public static void onProjectOpenFinished(@NotNull Project project, long time) {
    PROJECT_OPENING_FINISHED.log(project, time);
  }

  public static void onProjectOpened(@NotNull Project project) {
    PROJECT_OPENED.log(project);
  }

  public static void onProjectClosed(@NotNull Project project) {
    PROJECT_CLOSED.log(project);
  }

  public static void onProjectModuleAttached(@NotNull Project project) {
    PROJECT_MODULE_ATTACHED.log(project);
  }

  public static void onProtocolOpenCommandHandled(@Nullable Project project) {
    PROTOCOL_OPEN_COMMAND_HANDLED.log(project);
  }

  public static void onFrameActivated(@Nullable Project project) {
    FRAME_ACTIVATED.log(project);
  }

  public static void onFrameDeactivated(@Nullable Project project) {
    FRAME_DEACTIVATED.log(project);
  }

  public static void onFreeze(long durationMs) {
    IDE_FREEZE.log(durationMs, toLengthGroup((int)(durationMs / 1000)));
  }

  public static void onError(@Nullable PluginId pluginId,
                             @Nullable Throwable throwable,
                             @Nullable VMOptions.MemoryKind memoryErrorKind) {
    try {
      final ThrowableDescription description = new ThrowableDescription(throwable);
      List<EventPair<?>> data = new ArrayList<>();
      data.add(EventFields.PluginInfo.with(pluginId == null ? getPlatformPlugin() : getPluginInfoById(pluginId)));
      data.add(errorField.with(description.getClassName()));

      if (memoryErrorKind != null) {
        data.add(memoryErrorKindField.with(memoryErrorKind));
      }

      if (ourErrorsRateThrottle.tryPass(System.currentTimeMillis())) {

        List<String> frames = description.getLastFrames(50);
        int framesHash = frames.hashCode();

        data.add(errorHashField.with(framesHash));

        if (ourErrorsIdentityThrottle.tryPass(framesHash, System.currentTimeMillis())) {
          data.add(errorFramesField.with(frames));
          data.add(errorSizeField.with(description.getSize()));
        }
      }
      else {
        data.add(tooManyErrorsField.with(true));
      }

      IDE_ERROR.log(data);
    }
    catch (Exception e) {
      LOG.warn(e);
    }
  }

  public static void onCrashDetected() {
    IDE_CRASH_DETECTED.log();
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

  public static void onProjectFrameSelected(int option) {
    ProjectOpenMode optionValue;
    switch (option) {
      case GeneralSettings.OPEN_PROJECT_NEW_WINDOW:
        optionValue = ProjectOpenMode.New;
        break;

      case GeneralSettings.OPEN_PROJECT_SAME_WINDOW:
        optionValue = ProjectOpenMode.Same;
        break;

      case GeneralSettings.OPEN_PROJECT_SAME_WINDOW_ATTACH:
        optionValue = ProjectOpenMode.Attach;
        break;

      default:
        return;
    }
    PROJECT_FRAME_SELECTED.log(optionValue);
  }

  private static final class DurationEventField extends PrimitiveEventField<String> {
    @NotNull
    @Override
    public List<String> getValidationRule() {
      return Arrays.asList("{regexp#integer}s", "-{regexp#integer}s", "{regexp#integer}s+");
    }

    @Override
    public void addData(@NotNull FeatureUsageData fuData, String value) {
      if (value != null) {
        fuData.addData(getName(), value);
      }
    }

    @NotNull
    @Override
    public String getName() {
      return "duration_grouped";
    }
  }
}
