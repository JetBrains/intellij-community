package com.intellij.localvcsperf;

import com.intellij.localvcs.LocalVcs;
import com.intellij.localvcs.Path;
import com.intellij.localvcs.Storage;
import com.intellij.localvcs.TempDirTestCase;
import org.junit.Before;
import org.junit.BeforeClass;

public class PerformanceTest extends TempDirTestCase {
  private static String contentCache;
  protected LocalVcs vcs;

  @BeforeClass
  public static void prepareVcs() {
    LocalVcs vcs = new LocalVcs(new Storage(classTempDir));
    createChildren(vcs, p(""), 3);
    vcs.apply();
    vcs.store();
  }

  @Before
  public void loadVcs() {
    vcs = new LocalVcs(new Storage(classTempDir));
  }

  private static void createChildren(LocalVcs vcs,
                                     Path parent,
                                     Integer countdown) {
    if (countdown == 0) return;

    for (Integer i = 0; i < 10; i++) {
      int entryIndex = (i + 1);
      vcs.createFile(parent.appendedWith("file" + entryIndex),
                     create10kContent());

      Path child = parent.appendedWith("dir" + entryIndex);
      vcs.createDirectory(child);
      createChildren(vcs, child, countdown - 1);
    }
  }

  private static String create10kContent() {
    if (contentCache == null) {
      StringBuffer buffer = new StringBuffer();
      for (int i = 0; i < 3000; i++) {
        buffer.append(i);
      }
      contentCache = buffer.toString();
    }
    return contentCache;
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
