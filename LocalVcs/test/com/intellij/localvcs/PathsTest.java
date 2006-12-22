package com.intellij.localvcs;

import org.junit.Test;

public class PathsTest extends TestCase {
  @Test
  public void testParent() {
    assertEquals("dir1/dir2", Paths.getParentOf("dir1/dir2/file"));
    assertNull(Paths.getParentOf("file"));
  }

  @Test
  public void testName() {
    assertEquals("file", Paths.getNameOf("file"));
    assertEquals("file", Paths.getNameOf("dir/file"));
  }

  @Test
  public void testAppending() {
    assertEquals("file1/file2", Paths.appended("file1", "file2"));
  }

  @Test
  public void testAppendingPathWithDriveLetter() {
    assertEquals("c:/root/file", Paths.appended("c:/root", "file"));
  }

  @Test
  public void testRenaming() {
    assertEquals("dir/file2", Paths.renamed("dir/file1", "file2"));
    assertEquals("file2", Paths.renamed("file1", "file2"));
  }
  
  @Test
  public void testStartsWith() {
    assertTrue(Paths.startsWith("dir/file1", "dir"));

    Paths.setCaseSensitive(true);
    assertFalse(Paths.startsWith("dir/file1", "DiR"));

    Paths.setCaseSensitive(false);
    assertTrue(Paths.startsWith("dir/file1", "DiR"));
  }
}
