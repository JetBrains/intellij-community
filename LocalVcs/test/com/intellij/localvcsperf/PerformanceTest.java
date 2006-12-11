package com.intellij.localvcsperf;

import com.intellij.localvcs.LocalVcs;
import com.intellij.localvcs.Storage;
import com.intellij.localvcs.TempDirTestCase;
import org.junit.Before;
import org.junit.Test;

import java.util.Random;

public class PerformanceTest extends TempDirTestCase {
  private LocalVcs vcs;
  private Random random = new Random();

  @Before
  public void setUp() {
    vcs = createVcs();
  }

  private LocalVcs createVcs() {
    return new LocalVcs(new Storage(tempDir));
  }

  @Test
  public void testRegisteringChanges() {
    assertExecutionTime(200, new Task() {
      public void execute() {
        build100kEntriesTree();
      }
    });
  }

  @Test
  public void testApplyingChanges() {
    build100kEntriesTree();

    assertExecutionTime(1000, new Task() {
      public void execute() {
        vcs.apply();
      }
    });
  }

  @Test
  public void testSaving() {
    build100kEntriesTree();
    vcs.apply();

    assertExecutionTime(1000, new Task() {
      public void execute() {
        vcs.store();
      }
    });
  }

  @Test
  public void testLoading() {
    build100kEntriesTree();
    vcs.apply();
    vcs.store();

    assertExecutionTime(1000, new Task() {
      public void execute() {
        createVcs();
      }
    });
  }

  @Test
  public void testSearchingEntries() {
    build100kEntriesTree();
    vcs.apply();

    assertExecutionTime(150, new Task() {
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

  private void build100kEntriesTree() {
    vcs.createDirectory("root", null);
    createChildren("root", 5);
  }

  private void createChildren(String parent, Integer countdown) {
    if (countdown == 0) return;

    for (Integer i = 0; i < 10; i++) {
      vcs.createFile(parent + "/file" + i, null, null);

      String childPath = parent + "/dir" + i;
      vcs.createDirectory(childPath, null);
      createChildren(childPath, countdown - 1);
    }
  }

  protected void assertExecutionTime(long expected, final Task task) {
    long start = System.currentTimeMillis();
    task.execute();

    long actual = System.currentTimeMillis() - start;
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

  protected interface Task {
    void execute();
  }
}
