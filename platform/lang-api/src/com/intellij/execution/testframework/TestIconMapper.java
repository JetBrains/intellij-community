/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution.testframework;

import com.intellij.execution.testframework.sm.runner.states.TestStateInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Dmitry Avdeev
 */
public class TestIconMapper implements PoolOfTestIcons {

  private final static Map<Integer, TestStateInfo.Magnitude> magnitudes = new HashMap<>();

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
}
