/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.historyPerfTests;

import com.intellij.history.core.TempDirTestCase;
import com.intellij.history.utils.RunnableAdapter;

import java.util.Random;

public abstract class PerformanceTestCase extends TempDirTestCase {
  private final Random random = new Random();

  protected int rand(int max) {
    return random.nextInt(max);
  }

  protected void assertExecutionTime(long expected, RunnableAdapter r) {
    long actual = measureExecutionTime(r);
    long delta = ((actual * 100) / expected) - 100;

    String message = "delta: " + delta + "% expected: " + expected + "ms actual: " + actual + "ms";
    assertTrue(message, Math.abs(delta) < 20);
  }

  private long measureExecutionTime(RunnableAdapter r) {
    try {
      gc();
      long start = System.currentTimeMillis();
      r.doRun();
      return System.currentTimeMillis() - start;
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  protected void assertMemoryUsage(long mb, RunnableAdapter t) {
    try {
      long before = memoryUsage();
      t.doRun();
      long after = memoryUsage();

      long delta = after - before;
      assertEquals(mb, delta);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  protected long memoryUsage() {
    gc();
    long total = mb(Runtime.getRuntime().totalMemory());
    long free = mb(Runtime.getRuntime().freeMemory());
    return total - free;
  }

  protected long mb(long b) {
    return b / (1024 * 1024);
  }

  protected void gc() {
    for (int i = 0; i < 10; i++) System.gc();
  }
}
