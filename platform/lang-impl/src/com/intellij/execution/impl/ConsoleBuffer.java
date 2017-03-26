/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.execution.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.util.SystemProperties;

public class ConsoleBuffer {
  public static boolean useCycleBuffer() {
    return !"disabled".equalsIgnoreCase(System.getProperty("idea.cycle.buffer.size"));
  }

  public static int getCycleBufferSize() {
    if (UISettings.getInstance().getOverrideConsoleCycleBufferSize()) {
      return UISettings.getInstance().getConsoleCycleBufferSizeKb() * 1024;
    }
    return getLegacyCycleBufferSize();
  }

  public static int getLegacyCycleBufferSize() {
    return SystemProperties.getIntProperty("idea.cycle.buffer.size", 1024) * 1024;
  }
}
