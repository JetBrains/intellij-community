package com.intellij.localvcs;

import org.junit.Test;

public class FilenameTest extends TestCase {
  @Test
  public void testParent() {
    FileName f = new FileName("dir1/dir2/file");
    assertEquals("dir1/dir2", f.getParent().getPath());
  }

  @Test
  public void testParentOfOnePartFile() {
    FileName f = new FileName("file");
    assertNull(f.getParent());
  }

  @Test
  public void testTail() {
    assertEquals(fn("file"), new FileName("file").getTail());
    assertEquals(fn("file"), new FileName("dir/file").getTail());
  }

  @Test
  public void testParts() {
    FileName f = new FileName("dir1/dir2/file");
    assertElements(new Object[]{"dir1", "dir2", "file"}, f.getParts());
  }

  @Test
  public void testPartsOnOnePartFile() {
    FileName f = new FileName("file");
    assertElements(new Object[]{"file"}, f.getParts());
  }

  @Test
  public void testAppending() {
    FileName f1 = new FileName("file1");

    assertEquals(new FileName("file1/file2"),
                 f1.appendedWith(new FileName("file2")));
  }

  @Test
  public void testRenaming() {
    FileName f = new FileName("file1");

    assertEquals(new FileName("file2"), f.renamedWith(new FileName("file2")));
  }

  @Test
  public void testRenamingWithParent() {
    FileName f = new FileName("dir/file1");

    assertEquals(new FileName("dir/file2"),
                 f.renamedWith(new FileName("file2")));
  }
}
