/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInspection.dataFlow;

import org.jetbrains.annotations.Nullable;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

/**
 * @author peter
 */
public class WorkingTimeMeasurer {
  private final long myTimeLimit;
  private final long myStart;
  @Nullable private static final ThreadMXBean ourThreadMXBean;

  static {
    ThreadMXBean bean = ManagementFactory.getThreadMXBean();
    ourThreadMXBean = bean.isCurrentThreadCpuTimeSupported() ? bean : null;
  }

  private static long getCurrentTime() {
    return ourThreadMXBean != null ? ourThreadMXBean.getCurrentThreadUserTime() : System.nanoTime();
  }

  public WorkingTimeMeasurer(long nanoLimit) {
    myTimeLimit = nanoLimit;
    myStart = getCurrentTime();
  }

  public boolean isTimeOver() {
    return getCurrentTime() - myStart > myTimeLimit;
  }
}
