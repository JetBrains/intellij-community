package com.intellij.localvcsperf;

import com.intellij.localvcs.LocalVcs;
import com.intellij.localvcs.Storage;
import com.intellij.localvcs.TempDirTestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Random;

public class PerformanceTest extends TempDirTestCase {
  private LocalVcs vcs;
  private Random random = new Random();
  private Storage s;

  @Before
  public void initVcs() {
    closeStorage();
    s = new Storage(tempDir);
    vcs = new LocalVcs(s);
  }

  @After
  public void closeStorage() {
    if (s != null) s.close();
  }

  @Test
  public void testRegisteringChanges() {
    assertExecutionTime(200, new Task() {
      public void execute() {
        buildTree();
      }
    });
  }

  @Test
  public void testApplyingChanges() {
    buildTree();

    assertExecutionTime(1000, new Task() {
      public void execute() {
        vcs.apply();
      }
    });
  }

  @Test
  public void testSaving() {
    buildTree();
    vcs.apply();

    assertExecutionTime(1000, new Task() {
      public void execute() {
        vcs.store();
      }
    });
  }

  @Test
  public void testLoading() {
    buildTree();
    vcs.apply();
    vcs.store();

    assertExecutionTime(1000, new Task() {
      public void execute() {
        initVcs();
      }
    });
  }

  @Test
  public void testSearchingEntries() {
    buildTree();
    vcs.apply();

    assertExecutionTime(200, new Task() {
      public void execute() {
        for (int i = 0; i < 10000; i++) {
          vcs.getEntry("root/dir" + rand(10) + "/dir" + rand(10) + "/dir" + rand(10) + "/file" + rand(10));
        }
      }
    });
  }

  private int rand(int max) {
    return random.nextInt(max);
  }

  private void buildTree() {
    vcs.createDirectory("root", null);
    createChildren("root", 5);
  }

  private void createChildren(String parent, Integer countdown) {
    if (countdown == 0) return;

    for (Integer i = 0; i < 10; i++) {
      String filePath = parent + "/file" + i;
      vcs.createFile(filePath, null, null);

      String dirPath = parent + "/dir" + i;
      vcs.createDirectory(dirPath, null);
      createChildren(dirPath, countdown - 1);
    }
  }

  protected void assertExecutionTime(long expected, final Task task) {
    long actual = measureExecutionTime(task);
    long delta = ((actual * 100) / expected) - 100;

    String message = "delta: " + delta + " expected: " + expected + "ms actual: " + actual + "ms";
    boolean success = delta < 10;
    if (success) {
      System.out.println("success with " + message);
    }
    else {
      fail("failure with " + message);
    }
  }

  private long measureExecutionTime(Task task) {
    long start = System.currentTimeMillis();
    task.execute();
    return System.currentTimeMillis() - start;
  }

  protected interface Task {
    void execute();
  }
}
