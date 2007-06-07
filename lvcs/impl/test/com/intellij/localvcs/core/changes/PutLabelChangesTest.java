package com.intellij.localvcs.core.changes;

import com.intellij.localvcs.core.LocalVcsTestCase;
import com.intellij.localvcs.core.tree.Entry;
import com.intellij.localvcs.core.tree.RootEntry;
import org.junit.Before;
import org.junit.Test;

public class PutLabelChangesTest extends LocalVcsTestCase {
  Entry r;

  @Before
  public void setUp() {
    r = new RootEntry();
    createDirectory(r, 1, "dir");
    createDirectory(r, 2, "dir/subDir");
    createFile(r, 3, "dir/subDir/file", null, -1);
  }

  @Test
  public void testLabelOnFile() {
    Change c = new PutEntryLabelChange("dir/subDir/file", null, -1, false);
    c.applyTo(r);

    assertFalse(c.affects(r.getEntry("dir")));
    assertFalse(c.affects(r.getEntry("dir/subDir")));
    assertTrue(c.affects(r.getEntry("dir/subDir/file")));
  }

  @Test
  public void testLabelOnDirectory() {
    Change c = new PutEntryLabelChange("dir/subDir", null, -1, false);
    c.applyTo(r);

    assertFalse(c.affects(r.getEntry("dir")));
    assertTrue(c.affects(r.getEntry("dir/subDir")));
    assertTrue(c.affects(r.getEntry("dir/subDir/file")));
  }

  @Test
  public void testGlobalLabel() {
    Change c = new PutLabelChange(null, -1, false);
    c.applyTo(r);

    assertTrue(c.affects(r.getEntry("dir")));
    assertTrue(c.affects(r.getEntry("dir/subDir")));
    assertTrue(c.affects(r.getEntry("dir/subDir/file")));
  }
}