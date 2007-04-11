package com.intellij.localvcsperf;

import com.intellij.localvcs.core.TempDirTestCase;

import java.util.Random;

public abstract class PerformanceTestCase extends TempDirTestCase {
  private Random random = new Random();

  protected int rand(int max) {
    return random.nextInt(max);
  }

  protected void assertExecutionTime(long expected, Task task) {
    long actual = measureExecutionTime(task);
    long delta = ((actual * 100) / expected) - 100;

    String message = "delta: " + delta + "% expected: " + expected + "ms actual: " + actual + "ms";
    assertTrue(message, Math.abs(delta) < 20);
  }

  private long measureExecutionTime(Task task) {
    try {
      gc();
      long start = System.currentTimeMillis();
      task.execute();
      return System.currentTimeMillis() - start;
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  protected void assertMemoryUsage(long mb, Task t) {
    try {
      long before = memoryUsage();
      t.execute();
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

  protected interface Task {
    void execute() throws Exception;
  }
}
