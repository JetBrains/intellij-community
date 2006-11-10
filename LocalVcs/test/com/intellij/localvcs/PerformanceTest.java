package com.intellij.localvcs;

import org.junit.Before;

public class PerformanceTest extends TempDirTestCase {
  protected LocalVcs vcs;

  @Before
  public void setUp() {
    vcs = new LocalVcs(new Storage(tempDir));

    createChildren(p(""), 3);
    vcs.apply();
  }

  private void createChildren(Path parent, Integer countdown) {
    if (countdown == 0) return;

    for (Integer i = 0; i < 10; i++) {
      int entryIndex = (i + 1);
      vcs.createFile(parent.appendedWith("file" + entryIndex),
                     createContent(entryIndex));

      Path child = parent.appendedWith("dir" + entryIndex);
      vcs.createDirectory(child);
      createChildren(child, countdown - 1);
    }
  }

  private String createContent(int entryIndex) {
    return "content" + entryIndex;
  }

  protected void assertExecutionTime(long expectedTime, final Task task) {
    long start = System.currentTimeMillis();
    task.execute();

    long actualTime = System.currentTimeMillis() - start;

    assertTrue("task took more time: expected "
               + expectedTime + " actual " + actualTime,
               actualTime < expectedTime);
  }

  protected interface Task {
    void execute();
  }
}
