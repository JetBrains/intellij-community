// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.featureStatistics.fusCollectors;

import com.intellij.diagnostic.VMOptions;
import com.intellij.ide.GeneralSettings;
import com.intellij.internal.DebugAttachDetector;
import com.intellij.internal.statistic.collectors.fus.MethodNameRuleValidator;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.*;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.Strings;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

import static com.intellij.internal.statistic.utils.PluginInfoDetectorKt.getPlatformPlugin;
import static com.intellij.internal.statistic.utils.PluginInfoDetectorKt.getPluginInfoById;

@ApiStatus.Internal
public final class LifecycleUsageTriggerCollector extends CounterUsagesCollector {
  private static final Logger LOG = Logger.getInstance(LifecycleUsageTriggerCollector.class);

  private static final EventLogGroup LIFECYCLE = new EventLogGroup("lifecycle", 70);

  private static final EventField<Boolean> eapField = EventFields.Boolean("eap");
  private static final EventField<Boolean> testField = EventFields.Boolean("test");
  private static final EventField<Boolean> commandLineField = EventFields.Boolean("command_line");
  private static final EventField<Boolean> internalField = EventFields.Boolean("internal");
  private static final EventField<Boolean> headlessField = EventFields.Boolean("headless");
  private static final EventField<Boolean> debugAgentField = EventFields.Boolean("debug_agent");
  private static final VarargEventId IDE_EVENT_START = LIFECYCLE.registerVarargEvent(
    "ide.start", eapField, testField, commandLineField, internalField, headlessField, debugAgentField);

  private static final EventId1<Boolean> IDE_CLOSE = LIFECYCLE.registerEvent("ide.close", EventFields.Boolean("restart"));

  private static final EventId2<Long, Boolean> PROJECT_OPENING_FINISHED =
    LIFECYCLE.registerEvent("project.opening.finished", EventFields.Long("duration_ms"), EventFields.Boolean("project_tab"));

  private static final EventId PROJECT_OPENED = LIFECYCLE.registerEvent("project.opened");

  private static final EventId PROJECT_CLOSED = LIFECYCLE.registerEvent("project.closed"); // actually called before closed and disposed

  private static final EventField<Long> projectTotalCloseDurationField = EventFields.Long("total_duration_ms");
  private static final EventField<Long> projectSaveDurationField = EventFields.Long("save_duration_ms");
  private static final EventField<Long> projectClosingDurationField = EventFields.Long("closing_duration_ms");
  private static final EventField<Long> projectDisposeDurationField = EventFields.Long("dispose_duration_ms");
  private static final VarargEventId PROJECT_CLOSED_AND_DISPOSED = LIFECYCLE.registerVarargEvent(
    "project.closed.and.disposed", projectTotalCloseDurationField, projectSaveDurationField, projectClosingDurationField,
    projectDisposeDurationField);

  private static final EventId PROJECT_MODULE_ATTACHED = LIFECYCLE.registerEvent("project.module.attached");

  private static final EventId PROTOCOL_OPEN_COMMAND_HANDLED = LIFECYCLE.registerEvent("protocol.open.command.handled");

  private static final EventId FRAME_ACTIVATED = LIFECYCLE.registerEvent("frame.activated");

  private static final EventId FRAME_DEACTIVATED = LIFECYCLE.registerEvent("frame.deactivated");

  private static final EventId1<Long> IDE_FREEZE = LIFECYCLE.registerEvent("ide.freeze", EventFields.Long("duration_ms"));

  private static final ClassEventField errorField = EventFields.Class("error");
  private static final EventField<VMOptions.MemoryKind> memoryErrorKindField =
    EventFields.Enum("memory_error_kind", VMOptions.MemoryKind.class, kind -> Strings.toLowerCase(kind.name()));
  private static final EventField<Integer> errorHashField = EventFields.Int("error_hash");
  private static final StringListEventField errorFramesField =
    EventFields.StringListValidatedByCustomRule("error_frames", MethodNameRuleValidator.class);
  private static final EventField<Integer> errorSizeField = EventFields.Int("error_size");
  private static final EventField<Boolean> tooManyErrorsField = EventFields.Boolean("too_many_errors");
  private static final VarargEventId IDE_ERROR = LIFECYCLE.registerVarargEvent(
    "ide.error", EventFields.PluginInfo, errorField, memoryErrorKindField, errorHashField, errorFramesField, errorSizeField, tooManyErrorsField);

  private static final EventId IDE_CRASH_DETECTED = LIFECYCLE.registerEvent("ide.crash.detected");

  private static final EventId IDE_DEADLOCK_DETECTED = LIFECYCLE.registerEvent("ide.deadlock.detected");

  private enum ProjectOpenMode {New, Same, Attach}
  private static final EventField<ProjectOpenMode> projectOpenModeField = EventFields.Enum("mode", ProjectOpenMode.class, mode -> Strings.toLowerCase(mode.name()));
  private static final EventId1<ProjectOpenMode> PROJECT_FRAME_SELECTED = LIFECYCLE.registerEvent("project.frame.selected", projectOpenModeField);

  private static final EventId1<Integer> EARLY_ERRORS =
    LIFECYCLE.registerEvent("early.errors", EventFields.Int("errors_ignored"));

  private static final EventsRateThrottle ourErrorRateThrottle = new EventsRateThrottle(100, 5L * 60 * 1000); // 100 errors per 5 minutes
  private static final EventsIdentityThrottle ourErrorIdentityThrottle = new EventsIdentityThrottle(50, 60L * 60 * 1000); // 1 unique error per 1 hour

  @Override
  public EventLogGroup getGroup() {
    return LIFECYCLE;
  }

  public static void onIdeStart() {
    var app = ApplicationManager.getApplication();
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

  public static void onProjectOpenFinished(@NotNull Project project, long time, boolean isTab) {
    PROJECT_OPENING_FINISHED.log(project, time, isTab);
  }

  public static void onProjectOpened(@NotNull Project project) {
    PROJECT_OPENED.log(project);
  }

  public static void onBeforeProjectClosed(@NotNull Project project) {
    PROJECT_CLOSED.log(project);
  }

  public static void onProjectClosedAndDisposed(
    @NotNull Project project,
    long closeStartedMs,
    long saveSettingsDurationMs,
    long closingDurationMs,
    long disposeDurationMs
  ) {
    long totalCloseDurationMs = System.currentTimeMillis() - closeStartedMs;
    PROJECT_CLOSED_AND_DISPOSED.log(
      project,
      projectTotalCloseDurationField.with(totalCloseDurationMs),
      projectSaveDurationField.with(saveSettingsDurationMs),
      projectClosingDurationField.with(closingDurationMs),
      projectDisposeDurationField.with(disposeDurationMs));
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
    IDE_FREEZE.log(durationMs);
  }

  public static void onError(
    @Nullable PluginId pluginId,
    @NotNull Throwable throwable,
    @Nullable VMOptions.MemoryKind memoryErrorKind
  ) {
    try {
      var description = new ThrowableDescription(throwable);
      var data = new ArrayList<EventPair<?>>();
      data.add(EventFields.PluginInfo.with(pluginId == null ? getPlatformPlugin() : getPluginInfoById(pluginId)));
      data.add(errorField.with(description.getThrowableClass()));

      if (memoryErrorKind != null) {
        data.add(memoryErrorKindField.with(memoryErrorKind));
      }

      if (ourErrorRateThrottle.tryPass(System.currentTimeMillis())) {
        var frames = description.getLastFrames(50);
        var frameHash = frames.hashCode();

        data.add(errorHashField.with(frameHash));

        if (ourErrorIdentityThrottle.tryPass(frameHash, System.currentTimeMillis())) {
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

  public static void onDeadlockDetected() {
    IDE_DEADLOCK_DETECTED.log();
  }

  public static void onProjectFrameSelected(int option) {
    ProjectOpenMode optionValue;
    switch (option) {
      case GeneralSettings.OPEN_PROJECT_NEW_WINDOW -> optionValue = ProjectOpenMode.New;
      case GeneralSettings.OPEN_PROJECT_SAME_WINDOW -> optionValue = ProjectOpenMode.Same;
      case GeneralSettings.OPEN_PROJECT_SAME_WINDOW_ATTACH -> optionValue = ProjectOpenMode.Attach;
      default -> {
        return;
      }
    }
    PROJECT_FRAME_SELECTED.log(optionValue);
  }

  public static void onEarlyErrorsIgnored(int numErrors) {
    EARLY_ERRORS.log(numErrors);
  }
}
