package com.intellij.localvcs;

import org.junit.Test;

public class PathTest extends TestCase {
  @Test
  public void testParent() {
    assertEquals("dir1/dir2", Path.getParentOf("dir1/dir2/file"));
    assertNull(Path.getParentOf("file"));
  }

  @Test
  public void testName() {
    assertEquals("file", Path.getNameOf("file"));
    assertEquals("file", Path.getNameOf("dir/file"));
  }

  @Test
  public void testAppending() {
    assertEquals("file1/file2", Path.appended("file1", "file2"));
  }

  @Test
  public void testAppendingPathWithDriveLetter() {
    assertEquals("c:/root/file", Path.appended("c:/root", "file"));
  }

  @Test
  public void testRenaming() {
    assertEquals("dir/file2", Path.renamed("dir/file1", "file2"));
    assertEquals("file2", Path.renamed("file1", "file2"));
  }
  
  @Test
  public void testStartsWith() {
    assertTrue(Path.startsWith("dir/file1", "dir"));

    Path.setCaseSensitive(true);
    assertFalse(Path.startsWith("dir/file1", "DiR"));

    Path.setCaseSensitive(false);
    assertTrue(Path.startsWith("dir/file1", "DiR"));
  }
}
