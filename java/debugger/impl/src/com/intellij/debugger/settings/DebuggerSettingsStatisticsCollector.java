// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.settings;

import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.VarargEventId;
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

import static com.intellij.internal.statistic.beans.MetricEventUtilKt.addBoolIfDiffers;

public final class DebuggerSettingsStatisticsCollector extends ApplicationUsagesCollector {
  private static final EventLogGroup GROUP = new EventLogGroup("debugger.settings.ide", 7);

  private static final VarargEventId SHOW_ALTERNATIVE_SOURCE = GROUP.registerVarargEvent("showAlternativeSource", EventFields.Enabled);
  private static final VarargEventId HOTSWAP_IN_BACKROUND = GROUP.registerVarargEvent("hotswapInBackround", EventFields.Enabled);
  private static final VarargEventId ENABLE_MEMORY_AGENT = GROUP.registerVarargEvent("enableMemoryAgent", EventFields.Enabled);
  private static final VarargEventId ALWAYS_SMART_STEP_INTO = GROUP.registerVarargEvent("alwaysSmartStepInto", EventFields.Enabled);
  private static final VarargEventId SKIP_CONSTRUCTORS = GROUP.registerVarargEvent("skipConstructors", EventFields.Enabled);
  private static final VarargEventId SKIP_GETTERS = GROUP.registerVarargEvent("skipGetters", EventFields.Enabled);
  private static final VarargEventId SKIP_CLASSLOADERS = GROUP.registerVarargEvent("skipClassloaders", EventFields.Enabled);
  private static final VarargEventId COMPILE_BEFORE_HOTSWAP = GROUP.registerVarargEvent("compileBeforeHotswap", EventFields.Enabled);
  private static final VarargEventId HOTSWAP_SHOW_FLOATING_BUTTON = GROUP.registerVarargEvent("showHotSwapButtonInEditor", EventFields.Enabled);
  private static final VarargEventId HOTSWAP_HANG_WARNING_ENABLED = GROUP.registerVarargEvent("hotswapHangWarningEnabled", EventFields.Enabled);
  private static final VarargEventId WATCH_RETURN_VALUES = GROUP.registerVarargEvent("watchReturnValues", EventFields.Enabled);
  private static final VarargEventId AUTO_VARIABLES_MODE = GROUP.registerVarargEvent("autoVariablesMode", EventFields.Enabled);
  private static final VarargEventId KILL_PROCESS_IMMEDIATELY = GROUP.registerVarargEvent("killProcessImmediately", EventFields.Enabled);
  private static final VarargEventId RESUME_ONLY_CURRENT_THREAD = GROUP.registerVarargEvent("resumeOnlyCurrentThread", EventFields.Enabled);
  private static final VarargEventId HIDE_STACK_FRAMES_USING_STEPPING_FILTER = GROUP.registerVarargEvent("hideStackFramesUsingSteppingFilter", EventFields.Enabled);
  private static final VarargEventId INSTRUMENTING_AGENT = GROUP.registerVarargEvent("instrumentingAgent", EventFields.Enabled);

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  @NotNull
  @Override
  public Set<MetricEvent> getMetrics() {
    Set<MetricEvent> set = new HashSet<>();

    DebuggerSettings settings = DebuggerSettings.getInstance();
    DebuggerSettings sDefault = new DebuggerSettings();

    addBoolIfDiffers(set, settings, sDefault, s -> s.SHOW_ALTERNATIVE_SOURCE, SHOW_ALTERNATIVE_SOURCE);
    addBoolIfDiffers(set, settings, sDefault, s -> s.ENABLE_MEMORY_AGENT, ENABLE_MEMORY_AGENT);
    addBoolIfDiffers(set, settings, sDefault, s -> s.ALWAYS_SMART_STEP_INTO, ALWAYS_SMART_STEP_INTO);
    addBoolIfDiffers(set, settings, sDefault, s -> s.SKIP_CONSTRUCTORS, SKIP_CONSTRUCTORS);
    addBoolIfDiffers(set, settings, sDefault, s -> s.SKIP_GETTERS, SKIP_GETTERS);
    addBoolIfDiffers(set, settings, sDefault, s -> s.SKIP_CLASSLOADERS, SKIP_CLASSLOADERS);
    addBoolIfDiffers(set, settings, sDefault, s -> s.COMPILE_BEFORE_HOTSWAP, COMPILE_BEFORE_HOTSWAP);
    addBoolIfDiffers(set, settings, sDefault, s -> s.HOTSWAP_SHOW_FLOATING_BUTTON, HOTSWAP_SHOW_FLOATING_BUTTON);
    addBoolIfDiffers(set, settings, sDefault, s -> s.HOTSWAP_HANG_WARNING_ENABLED, HOTSWAP_HANG_WARNING_ENABLED);
    addBoolIfDiffers(set, settings, sDefault, s -> s.WATCH_RETURN_VALUES, WATCH_RETURN_VALUES);
    addBoolIfDiffers(set, settings, sDefault, s -> s.AUTO_VARIABLES_MODE, AUTO_VARIABLES_MODE);
    addBoolIfDiffers(set, settings, sDefault, s -> s.KILL_PROCESS_IMMEDIATELY, KILL_PROCESS_IMMEDIATELY);
    addBoolIfDiffers(set, settings, sDefault, s -> s.RESUME_ONLY_CURRENT_THREAD, RESUME_ONLY_CURRENT_THREAD);
    addBoolIfDiffers(set, settings, sDefault, s -> s.HIDE_STACK_FRAMES_USING_STEPPING_FILTER, HIDE_STACK_FRAMES_USING_STEPPING_FILTER);
    addBoolIfDiffers(set, settings, sDefault, s -> s.INSTRUMENTING_AGENT, INSTRUMENTING_AGENT);

    return set;
  }
}
