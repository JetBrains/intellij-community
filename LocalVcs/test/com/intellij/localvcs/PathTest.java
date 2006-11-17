package com.intellij.localvcs;

import org.junit.Test;

public class PathTest extends TestCase {
  @Test
  public void testParent() {
    Path p = new Path("dir1/dir2/file");
    assertEquals(new Path("dir1/dir2"), p.getParent());
  }

  @Test
  public void testParentOfOnePartFile() {
    Path p = new Path("file");
    assertNull(p.getParent());
  }

  @Test
  public void testName() {
    assertEquals("file", new Path("file").getName());
    assertEquals("file", new Path("dir/file").getName());
  }

  @Test
  public void testAppending() {
    Path p1 = new Path("file1");

    assertEquals(new Path("file1/file2"), p1.appendedWith("file2"));
  }

  @Test
  public void testParts() {
    Path p = new Path("dir1/dir2/file");
    assertElements(new Object[]{"dir1", "dir2", "file"}, p.getParts());
  }

  @Test
  public void testPartsOnOnePartFile() {
    Path p = new Path("file");
    assertElements(new Object[]{"file"}, p.getParts());
  }
}
