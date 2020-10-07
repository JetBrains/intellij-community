// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework;

import com.intellij.execution.testframework.sm.runner.states.TestStateInfo;
import com.intellij.icons.AllIcons;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
public final class TestIconMapper implements PoolOfTestIcons {
  private final static Int2ObjectMap<TestStateInfo.Magnitude> magnitudes = new Int2ObjectOpenHashMap<>();

  static {
    for (TestStateInfo.Magnitude value : TestStateInfo.Magnitude.values()) {
      magnitudes.put(value.getValue(), value);
    }
  }

  public static TestStateInfo.Magnitude getMagnitude(int value) {
    return magnitudes.get(value);
  }

  @Nullable
  public static Icon getIcon(@NotNull TestStateInfo.Magnitude magnitude) {
    switch (magnitude) {
      case SKIPPED_INDEX:
        return SKIPPED_ICON;
      case COMPLETE_INDEX:
        return PASSED_ICON;
      case NOT_RUN_INDEX:
        return NOT_RAN;
      case RUNNING_INDEX:
        return null;
      case TERMINATED_INDEX:
        return TERMINATED_ICON;
      case IGNORED_INDEX:
        return IGNORED_ICON;
      case FAILED_INDEX:
        return FAILED_ICON;
      case ERROR_INDEX:
        return ERROR_ICON;
      case PASSED_INDEX:
        return PASSED_ICON;
    }
    return null;
  }

  @Nullable
  public static Icon getToolbarIcon(@NotNull TestStateInfo.Magnitude magnitude) {
    switch (magnitude) {
      case SKIPPED_INDEX:
        return AllIcons.RunConfigurations.ToolbarSkipped;
      case COMPLETE_INDEX:
        return AllIcons.RunConfigurations.ToolbarPassed;
      case NOT_RUN_INDEX:
        return AllIcons.RunConfigurations.TestNotRan;
      case TERMINATED_INDEX:
        return AllIcons.RunConfigurations.ToolbarTerminated;
      case IGNORED_INDEX:
        return AllIcons.RunConfigurations.ShowIgnored;
      case FAILED_INDEX:
        return AllIcons.RunConfigurations.ToolbarFailed;
      case ERROR_INDEX:
        return AllIcons.RunConfigurations.ToolbarError;
      case PASSED_INDEX:
        return AllIcons.RunConfigurations.ToolbarPassed;
      default:
        return null;
    }
  }
}
