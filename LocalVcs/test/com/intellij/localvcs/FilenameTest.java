package com.intellij.localvcs;

import org.junit.Test;

public class FilenameTest extends TestCase {
  @Test
  public void testParent() {
    Filename f = new Filename("dir1/dir2/file");
    assertEquals("dir1/dir2", f.getParent().getPath());
  }

  @Test
  public void testParentOfOnePartFile() {
    Filename f = new Filename("file");
    assertNull(f.getParent());
  }

  @Test
  public void testTail() {
    assertEquals(fn("file"), new Filename("file").getTail());
    assertEquals(fn("file"), new Filename("dir/file").getTail());
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

  @Test
  public void testAppending() {
    Filename f1 = new Filename("file1");

    assertEquals(new Filename("file1/file2"),
                 f1.appendedWith(new Filename("file2")));
  }

  @Test
  public void testRenaming() {
    Filename f = new Filename("file1");

    assertEquals(new Filename("file2"), f.renamedWith(new Filename("file2")));
  }

  @Test
  public void testRenamingWithParent() {
    Filename f = new Filename("dir/file1");

    assertEquals(new Filename("dir/file2"),
                 f.renamedWith(new Filename("file2")));
  }
}
