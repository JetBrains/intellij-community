package com.intellij.localvcs;

import java.util.Arrays;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ChangeSetTest extends Assert {
  private ChangeSet myChangeSet;
  private String myLog;
  private Snapshot mySnapshot;

  @Before
  public void setUp() {
    myChangeSet = new ChangeSet(
        Arrays.asList(new Change[]{
            new CreateFileChange("file1", ""),
            new CreateFileChange("file2", ""),
            new CreateFileChange("file3", "")}));

    myLog = "";
    mySnapshot = new Snapshot() {
      @Override
      protected void createFile(String name, String content) {
        myLog += name + " ";
      }

      @Override
      protected void deleteFile(String name) {
        myLog += name + " ";
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
