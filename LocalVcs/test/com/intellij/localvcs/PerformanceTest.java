package com.intellij.localvcs;

import org.junit.Before;

public class PerformanceTest extends TempDirTestCase {
  private LocalVcs vcs;

  @Before
  public void setUp() {
    vcs = new LocalVcs(new Storage(tempDir));

    createChildren(p(""), 3);
    vcs.apply();
  }

  private void createChildren(Path parent, Integer countdown) {
    if (countdown == 0) return;

    for (Integer i = 0; i < 10; i++) {
      vcs.createFile(parent.appendedWith("file" + (i + 1)),
                     "content" + (i + 1));

      Path child = parent.appendedWith("dir" + (i + 1));
      vcs.createDirectory(child);
      createChildren(child, countdown - 1);
    }
  }

  //@Test
  public void testAddingEntries() {
    assertExecutionTime(10, new Task() {
      public void execute() {
        vcs.createFile(p("test"), "content");
        vcs.apply();
      }
    });
  }

  private void assertExecutionTime(long expectedTime, final Task task) {
    long start = System.currentTimeMillis();
    task.execute();

    long actualTime = System.currentTimeMillis() - start;

    assertTrue("task took more time: expected "
               + expectedTime + " actual " + actualTime,
               actualTime < expectedTime);
  }

  private interface Task {
    void execute();
  }
}
