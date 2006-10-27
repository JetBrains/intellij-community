package com.intellij.localvcs;

import org.junit.Before;
import org.junit.Test;

public class ChangeSetTest extends TestCase {
  private ChangeSet myChangeSet;
  private String myLog;
  private Snapshot mySnapshot;

  @Before
  public void setUp() {
    myChangeSet = cs(new CreateFileChange(fn("file1"), ""),
                     new CreateFileChange(fn("file2"), ""),
                     new CreateFileChange(fn("file3"), ""));

    myLog = "";
    mySnapshot = new Snapshot() {
      @Override
      protected void createFile(Filename name, String content) {
        myLog += name.getName() + " ";
      }

      @Override
      protected void deleteFile(Filename name) {
        myLog += name.getName() + " ";
      }
    };
  }

  @Test
  public void testApplyingIsFIFO() {
    myChangeSet.applyTo(mySnapshot);
    assertEquals("file1 file2 file3 ", myLog);
  }

  @Test
  public void testRevertingIsLIFO() {
    myChangeSet.revertOn(mySnapshot);
    assertEquals("file3 file2 file1 ", myLog);
  }
}
