package com.intellij.localvcsperf;

import com.intellij.localvcs.TempDirTestCase;

import java.util.Random;

public class PerformanceTest extends TempDirTestCase {
  private Random random = new Random();

  protected int rand(int max) {
    return random.nextInt(max);
  }

  protected void assertExecutionTime(long expected, Task task) {
    long actual = measureExecutionTime(task);
    long delta = ((actual * 100) / expected) - 100;

    String message = "delta: " + delta + "% expected: " + expected + "ms actual: " + actual + "ms";
    boolean success = delta < 10;
    if (success) {
      System.out.println("success with " + message);
    }
    else {
      fail("failure with " + message);
    }
  }

  private long measureExecutionTime(Task task) {
    try {
      long start = System.currentTimeMillis();
      task.execute();
      return System.currentTimeMillis() - start;
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  protected interface Task {
    void execute() throws Exception;
  }
}
