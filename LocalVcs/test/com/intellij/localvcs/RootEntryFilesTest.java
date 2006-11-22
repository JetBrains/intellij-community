package com.intellij.localvcs;

import org.junit.Test;

public class RootEntryFilesTest extends TestCase {
  private RootEntry root = new RootEntry("");

  @Test
  public void testCreatingFiles() {
    assertFalse(root.hasEntry("/file"));

    root.createFile(null, "/file", "content", 42L);
    assertTrue(root.hasEntry("/file"));

    Entry e = root.getEntry("/file");

    assertEquals(FileEntry.class, e.getClass());
    assertEquals("content", e.getContent());
    assertEquals(42L, e.getTimestamp());
  }

  @Test
  public void testCreatingTwoFiles() {
    root.createFile(null, "/file1", null, null);
    root.createFile(null, "/file2", null, null);

    assertTrue(root.hasEntry("/file1"));
    assertTrue(root.hasEntry("/file2"));

    assertFalse(root.hasEntry("/unknown file"));
  }

  @Test
  public void testCreatingFileWithExistingNameThrowsException() {
    root.createFile(null, "/file", null, null);

    try {
      root.createFile(null, "/file", null, null);
      fail();
    } catch (LocalVcsException e) {}
  }

  @Test
  public void testChangingFileContent() {
    root.createFile(1, "/file", "content", null);
    root.changeFileContent("/file", "new content", 77L);

    Entry e = root.getEntry("/file");

    assertEquals("new content", e.getContent());
    assertEquals(77L, e.getTimestamp());
  }

  @Test
  public void testChangingFileContentAffectsOnlyOneFile() {
    root.createFile(1, "/file1", "content1", null);
    root.createFile(2, "/file2", "content2", null);

    root.changeFileContent("/file1", "new content", null);

    assertEquals("new content", root.getEntry("/file1").getContent());
    assertEquals("content2", root.getEntry("/file2").getContent());
  }

  @Test
  public void testChangingContentOfAnNonExistingFileThrowsException() {
    try {
      root.changeFileContent("/unknown file", "content", null);
      fail();
    } catch (LocalVcsException e) {}
  }

  @Test
  public void testRenamingFiles() {
    root.createFile(1, "/file", "content", null);

    root.rename("/file", "new file", 54L);

    assertFalse(root.hasEntry("/file"));
    assertTrue(root.hasEntry("/new file"));

    Entry e = root.getEntry("/new file");

    assertEquals("content", e.getContent());
    assertEquals(54L, e.getTimestamp());
  }

  @Test
  public void testRenamingOfAnNonExistingFileThrowsException() {
    try {
      root.rename("/unknown file", "new file", null);
      fail();
    } catch (LocalVcsException e) {}
  }

  @Test
  public void testRenamingFileToAnExistingFileNameThrowsException() {
    root.createFile(1, "/file1", null, null);
    root.createFile(2, "/file2", null, null);

    try {
      root.rename("/file1", "file2", null);
      fail();
    } catch (LocalVcsException e) {}
  }

  @Test
  public void testRenamingFileToSameNameDoesNothing() {
    root.createFile(1, "/file", "content", null);

    root.rename("/file", "file", null);

    assertTrue(root.hasEntry("/file"));
    assertEquals("content", root.getEntry("/file").getContent());
  }

  @Test
  public void testDeletingFiles() {
    root.createFile(1, "/file", null, null);

    root.delete("/file");

    assertFalse(root.hasEntry("/file"));
  }

  @Test
  public void testDeletingOnlyOneFile() {
    root.createFile(1, "/file1", null, null);
    root.createFile(2, "/file2", null, null);

    root.delete("/file2");

    assertTrue(root.hasEntry("/file1"));
    assertFalse(root.hasEntry("/file2"));
  }

  @Test
  public void testDeletingOfAnUnknownEntryThrowsException() {
    try {
      root.delete("/unknown file");
      fail();
    } catch (LocalVcsException e) {}
  }
}
