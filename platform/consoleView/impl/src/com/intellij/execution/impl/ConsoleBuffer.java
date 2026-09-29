// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.util.SystemProperties;

public final class ConsoleBuffer {
  public static boolean useCycleBuffer() {
    return !"disabled".equalsIgnoreCase(System.getProperty("idea.cycle.buffer.size"));
  }

  public static int getCycleBufferSize() {
    UISettings uiSettings = UISettings.getInstance();
    if (uiSettings.getOverrideConsoleCycleBufferSize()) {
      return Math.min(Integer.MAX_VALUE / 1024, uiSettings.getConsoleCycleBufferSizeKb()) * 1024;
    }
    return getLegacyCycleBufferSize();
  }

  public static int getLegacyCycleBufferSize() {
    return Math.max(0, SystemProperties.getIntProperty("idea.cycle.buffer.size", 1024)) * 1024;
  }
}
