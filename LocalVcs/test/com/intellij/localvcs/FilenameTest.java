package com.intellij.localvcs;

import org.junit.Test;

public class FilenameTest extends TestCase {
  @Test
  public void testParent() {
    Filename f = new Filename("dir1/dir2/file");
    assertEquals("dir1/dir2", f.getParent().getName());
  }

  @Test
  public void testParentOfOnePartFile() {
    Filename f = new Filename("file");
    assertNull(f.getParent());
  }

  @Test
  public void testParts() {
    Filename f = new Filename("dir1/dir2/file");
    assertElements(new Object[]{"dir1", "dir2", "file"}, f.getParts());
  }

  @Test
  public void testPartsOnOnePartFile() {
    Filename f = new Filename("file");
    assertElements(new Object[]{"file"}, f.getParts());
  }
}
