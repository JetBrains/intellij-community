package com.intellij.localvcs;

import org.junit.Before;
import org.junit.Test;

public class PerformanceTest extends TempDirTestCase {
  private LocalVcs vcs;

  @Before
  public void setUp() {
    vcs = new LocalVcs(new Storage(tempDir));

    createChildren(p(""), 3);
  }

  private void createChildren(Path parent, Integer countdown) {
    if (countdown == 0) return;

    for (Integer i = 0; i < 50; i++) {
      Path child = parent.appendedWith("dir" + (i + 1));
      vcs.createDirectory(child);
      createChildren(child, countdown - 1);
    }
  }

  @Test
  public void test() {
    fail();
  }

  private void assertExecutionTime(long time, Task task) {

  }

  private interface Task {
    void execute();
  }
}
