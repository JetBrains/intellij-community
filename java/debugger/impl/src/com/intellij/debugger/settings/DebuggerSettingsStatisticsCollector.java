// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.settings;

import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector;
import com.intellij.openapi.util.text.StringUtil;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

import static com.intellij.internal.statistic.utils.StatisticsUtilKt.addIfDiffers;

public class DebuggerSettingsStatisticsCollector extends ApplicationUsagesCollector {
  @NotNull
  @Override
  public String getGroupId() {
    return "debugger.settings.ide";
  }

  @NotNull
  @Override
  public Set<UsageDescriptor> getUsages() {
    Set<UsageDescriptor> set = new HashSet<>();

    DebuggerSettings settings = DebuggerSettings.getInstance();
    DebuggerSettings sDefault = new DebuggerSettings();

    addBoolIfDiffers(set, settings, sDefault, s -> s.FORCE_CLASSIC_VM, "forceClassicVm");
    addBoolIfDiffers(set, settings, sDefault, s -> s.DISABLE_JIT, "disableJit");
    addBoolIfDiffers(set, settings, sDefault, s -> s.SHOW_ALTERNATIVE_SOURCE, "showAlternativeSource");
    addBoolIfDiffers(set, settings, sDefault, s -> s.HOTSWAP_IN_BACKGROUND, "hotswapInBackround");
    addBoolIfDiffers(set, settings, sDefault, s -> s.ENABLE_MEMORY_AGENT, "enableMemoryAgent");
    addBoolIfDiffers(set, settings, sDefault, s -> s.ALWAYS_SMART_STEP_INTO, "alwaysSmartStepInto");
    addBoolIfDiffers(set, settings, sDefault, s -> s.SKIP_CONSTRUCTORS, "skipConstructors");
    addBoolIfDiffers(set, settings, sDefault, s -> s.SKIP_GETTERS, "skipGetters");
    addBoolIfDiffers(set, settings, sDefault, s -> s.SKIP_CLASSLOADERS, "skipClassloaders");
    addBoolIfDiffers(set, settings, sDefault, s -> s.COMPILE_BEFORE_HOTSWAP, "compileBeforeHotswap");
    addBoolIfDiffers(set, settings, sDefault, s -> s.HOTSWAP_HANG_WARNING_ENABLED, "hotswapHangWarningEnabled");
    addBoolIfDiffers(set, settings, sDefault, s -> s.WATCH_RETURN_VALUES, "watchReturnValues");
    addBoolIfDiffers(set, settings, sDefault, s -> s.AUTO_VARIABLES_MODE, "autoVariablesMode");
    addBoolIfDiffers(set, settings, sDefault, s -> s.KILL_PROCESS_IMMEDIATELY, "killProcessImmediately");
    addBoolIfDiffers(set, settings, sDefault, s -> s.RESUME_ONLY_CURRENT_THREAD, "resumeOnlyCurrentThread");
    addBoolIfDiffers(set, settings, sDefault, s -> s.INSTRUMENTING_AGENT, "instrumentingAgent");

    return set;
  }

  private static <T> void addBoolIfDiffers(Set<UsageDescriptor> set, T settingsBean, T defaultSettingsBean,
                                           Function1<T, Boolean> valueFunction, String featureId) {
    addIfDiffers(set, settingsBean, defaultSettingsBean, valueFunction, (it) -> it ? featureId : "no" + StringUtil.capitalize(featureId));
  }
}
