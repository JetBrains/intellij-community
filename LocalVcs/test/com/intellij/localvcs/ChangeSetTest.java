package com.intellij.localvcs;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

public class ChangeSetTest extends TestCase {
  private ChangeSet myChangeSet;
  private List<Path> myLog;
  private Snapshot mySnapshot;

  @Before
  public void setUp() {
    myChangeSet = cs(new CreateFileChange(fn("file1"), null),
                     new CreateFileChange(fn("file2"), null),
                     new CreateFileChange(fn("file3"), null));

    myLog = new ArrayList<Path>();
    mySnapshot = new Snapshot() {
      @Override
      protected void doCreateFile(Path path, String content) {
        myLog.add(path);
      }

      @Override
      protected void doDelete(Path path) {
        myLog.add(path);
      }
    };
  }

  @Test
  public void testApplyingIsFIFO() {
    myChangeSet.applyTo(mySnapshot);
    assertElements(new Object[]{fn("file1"), fn("file2"), fn("file3")}, myLog);
  }

  @Test
  public void testRevertingIsLIFO() {
    myChangeSet.revertOn(mySnapshot);
    assertElements(new Object[]{fn("file3"), fn("file2"), fn("file1")}, myLog);
  }
}
