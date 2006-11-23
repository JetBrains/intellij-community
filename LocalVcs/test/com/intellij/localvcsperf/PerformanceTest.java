package com.intellij.localvcsperf;

import com.intellij.localvcs.LocalVcs;
import com.intellij.localvcs.TempDirTestCase;
import com.intellij.localvcs.TestStorage;
import org.junit.Before;
import org.junit.Test;

public class PerformanceTest extends TempDirTestCase {
  protected LocalVcs vcs;

  @Before
  public void prepareVcs() {
    vcs = new LocalVcs(new TestStorage());
    vcs.createDirectory("root", null);
    createChildren("root", 3);
    vcs.apply();
  }

  private void createChildren(String parent, Integer countdown) {
    if (countdown == 0) return;

    for (Integer i = 0; i < 10; i++) {
      int entryIndex = (i + 1);
      vcs.createFile(parent + "/file" + entryIndex, null, null);

      String childPath = parent + "/dir" + entryIndex;
      vcs.createDirectory(childPath, null);
      createChildren(childPath, countdown - 1);
    }
  }

  @Test
  public void testSearchingEntries() {
    assertExecutionTime(10, new Task() {
      public void execute() {
        for (int i = 0; i < 10000; i++) {
          vcs.getEntry("root/dir5/dir5/file5");
        }
      }
    });
  }

  protected void assertExecutionTime(long expectedTime, final Task task) {
    long start = System.currentTimeMillis();
    task.execute();

    long actualTime = System.currentTimeMillis() - start;

    String message = "task took more time: expected " + expectedTime + " actual " + actualTime;
    assertTrue(message, actualTime < expectedTime);
  }

  protected interface Task {
    void execute();
  }
}
