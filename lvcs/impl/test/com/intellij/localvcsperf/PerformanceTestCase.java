package com.intellij.localvcsperf;

import com.intellij.history.core.TempDirTestCase;
import com.intellij.history.utils.RunnableAdapter;

import java.util.Random;

public abstract class PerformanceTestCase extends TempDirTestCase {
  private Random random = new Random();

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
