// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo;
import com.intellij.icons.AllIcons;
import com.intellij.util.containers.IntObjectMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.BooleanSupplier;

/**
 * @author Dmitry Avdeev
 */
public final class TestIconMapper implements PoolOfTestIcons {
  private static final IntObjectMap<TestStateInfo.Magnitude> magnitudes = ConcurrentCollectionFactory.createConcurrentIntObjectMap();

  static {
    for (TestStateInfo.Magnitude value : TestStateInfo.Magnitude.values()) {
      magnitudes.put(value.getValue(), value);
    }
  }

  public static TestStateInfo.Magnitude getMagnitude(int value) {
    return magnitudes.get(value);
  }

  public static @Nullable Icon getIcon(@NotNull TestStateInfo.Magnitude magnitude) {
    return switch (magnitude) {
      case SKIPPED_INDEX -> SKIPPED_ICON;
      case COMPLETE_INDEX, PASSED_INDEX -> PASSED_ICON;
      case NOT_RUN_INDEX -> NOT_RAN;
      case RUNNING_INDEX -> null;
      case TERMINATED_INDEX -> TERMINATED_ICON;
      case IGNORED_INDEX -> IGNORED_ICON;
      case FAILED_INDEX -> FAILED_ICON;
      case ERROR_INDEX -> ERROR_ICON;
    };
  }

  public static @Nullable Icon getToolbarIcon(@NotNull TestStateInfo.Magnitude magnitude,
                                              boolean hasErrors,
                                              BooleanSupplier hasPassedTest) {
    return switch (magnitude) {
      case SKIPPED_INDEX -> AllIcons.RunConfigurations.ToolbarSkipped;
      case COMPLETE_INDEX, PASSED_INDEX -> AllIcons.RunConfigurations.ToolbarPassed;
      case NOT_RUN_INDEX -> AllIcons.RunConfigurations.TestNotRan;
      case TERMINATED_INDEX -> AllIcons.RunConfigurations.ToolbarTerminated;
      case IGNORED_INDEX -> !hasErrors && hasPassedTest.getAsBoolean() ? AllIcons.RunConfigurations.ToolbarPassedIgnored
                                                                       : AllIcons.RunConfigurations.ShowIgnored;
      case FAILED_INDEX -> AllIcons.RunConfigurations.ToolbarFailed;
      case ERROR_INDEX -> AllIcons.RunConfigurations.ToolbarError;
      default -> null;
    };
  }
}
