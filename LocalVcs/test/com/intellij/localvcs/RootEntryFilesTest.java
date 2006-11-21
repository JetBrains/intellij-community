package com.intellij.localvcs;

import org.junit.Test;

public class RootEntryFilesTest extends TestCase {
  private RootEntry root = new RootEntry("");

  @Test
  public void testCreatingFiles() {
    assertFalse(root.hasEntry(p("/file")));

    root.doCreateFile(null, p("/file"), "content", 42L);
    assertTrue(root.hasEntry(p("/file")));

    Entry e = root.getEntry(p("/file"));

    assertEquals(FileEntry.class, e.getClass());
    assertEquals("content", e.getContent());
    assertEquals(42L, e.getTimestamp());
  }

  @Test
  public void testCreatingTwoFiles() {
    root.doCreateFile(null, p("/file1"), null, null);
    root.doCreateFile(null, p("/file2"), null, null);

    assertTrue(root.hasEntry(p("/file1")));
    assertTrue(root.hasEntry(p("/file2")));

    assertFalse(root.hasEntry(p("/unknown file")));
  }

  @Test
  public void testCreatingFileWithExistingNameThrowsException() {
    root.doCreateFile(null, p("/file"), null, null);

    try {
      root.doCreateFile(null, p("/file"), null, null);
      fail();
    } catch (LocalVcsException e) {}
  }

  @Test
  public void testChangingFileContent() {
    root.doCreateFile(1, p("/file"), "content", null);
    root.doChangeFileContent(p("/file"), "new content", 77L);

    Entry e = root.getEntry(p("/file"));

    assertEquals("new content", e.getContent());
    assertEquals(77L, e.getTimestamp());
  }

  @Test
  public void testChangingFileContentAffectsOnlyOneFile() {
    root.doCreateFile(1, p("/file1"), "content1", null);
    root.doCreateFile(2, p("/file2"), "content2", null);

    root.doChangeFileContent(p("/file1"), "new content", null);

    assertEquals("new content", root.getEntry(p("/file1")).getContent());
    assertEquals("content2", root.getEntry(p("/file2")).getContent());
  }

  @Test
  public void testChangingContentOfAnNonExistingFileThrowsException() {
    try {
      root.doChangeFileContent(p("/unknown file"), "content", null);
      fail();
    } catch (LocalVcsException e) {}
  }

  @Test
  public void testRenamingFiles() {
    root.doCreateFile(1, p("/file"), "content", null);

    root.doRename(p("/file"), "new file", 54L);

    assertFalse(root.hasEntry(p("/file")));
    assertTrue(root.hasEntry(p("/new file")));

    Entry e = root.getEntry(p("/new file"));

    assertEquals("content", e.getContent());
    assertEquals(54L, e.getTimestamp());
  }

  @Test
  public void testRenamingOfAnNonExistingFileThrowsException() {
    try {
      root.doRename(p("/unknown file"), "new file", null);
      fail();
    } catch (LocalVcsException e) {}
  }

  @Test
  public void testRenamingFileToAnExistingFileNameThrowsException() {
    root.doCreateFile(1, p("/file1"), null, null);
    root.doCreateFile(2, p("/file2"), null, null);

    try {
      root.doRename(p("/file1"), "file2", null);
      fail();
    } catch (LocalVcsException e) {}
  }

  @Test
  public void testRenamingFileToSameNameDoesNothing() {
    root.doCreateFile(1, p("/file"), "content", null);

    root.doRename(p("/file"), "file", null);

    assertTrue(root.hasEntry(p("/file")));
    assertEquals("content", root.getEntry(p("/file")).getContent());
  }

  @Test
  public void testDeletingFiles() {
    root.doCreateFile(1, p("/file"), null, null);

    root.doDelete(p("/file"));

    assertFalse(root.hasEntry(p("/file")));
  }

  @Test
  public void testDeletingOnlyOneFile() {
    root.doCreateFile(1, p("/file1"), null, null);
    root.doCreateFile(2, p("/file2"), null, null);

    root.doDelete(p("/file2"));

    assertTrue(root.hasEntry(p("/file1")));
    assertFalse(root.hasEntry(p("/file2")));
  }

  @Test
  public void testDeletingOfAnUnknownEntryThrowsException() {
    try {
      root.doDelete(p("/unknown file"));
      fail();
    } catch (LocalVcsException e) {}
  }
}
