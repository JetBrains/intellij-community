package com.intellij.localvcs;

import org.junit.Before;
import org.junit.Test;

public class PutEntryLabelChangeTest extends LocalVcsTestCase {
  RootEntry r;

  @Before
  public void setUp() {
    r = new RootEntry();
    r.createDirectory(1, "dir");
    r.createDirectory(2, "dir/subDir");
    r.createFile(3, "dir/subDir/file", null, -1);
  }

  @Test
  public void testLabelOnFile() {
    Change c = new PutEntryLabelChange("dir/subDir/file", -1, null);
    c.applyTo(r);

    assertFalse(c.affects(r.getEntry("dir")));
    assertFalse(c.affects(r.getEntry("dir/subDir")));
    assertTrue(c.affects(r.getEntry("dir/subDir/file")));
  }

  @Test
  public void testLabelOnDirectory() {
    Change c = new PutEntryLabelChange("dir/subDir", -1, null);
    c.applyTo(r);

    assertFalse(c.affects(r.getEntry("dir")));
    assertTrue(c.affects(r.getEntry("dir/subDir")));
    assertTrue(c.affects(r.getEntry("dir/subDir/file")));
  }
}