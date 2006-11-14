package com.intellij.localvcs;

import org.junit.Test;

public class RootEntryFilesTest extends TestCase {
  private RootEntry root = new RootEntry();

  @Test
  public void testCreatingFiles() {
    assertFalse(root.hasEntry(p("file")));

    root.doCreateFile(null, p("file"), "content");
    assertTrue(root.hasEntry(p("file")));

    Entry e = root.getEntry(p("file"));

    assertEquals(FileEntry.class, e.getClass());
    assertEquals("content", e.getContent());
  }

  @Test
  public void testCreatingTwoFiles() {
    root.doCreateFile(null, p("file1"), null);
    root.doCreateFile(null, p("file2"), null);

    assertTrue(root.hasEntry(p("file1")));
    assertTrue(root.hasEntry(p("file2")));

    assertFalse(root.hasEntry(p("unknown file")));
  }

  @Test
  public void testCreatingFileWithExistingNameThrowsException() {
    root.doCreateFile(null, p("file"), null);

    try {
      root.doCreateFile(null, p("file"), null);
      fail();
    } catch (LocalVcsException e) {}
  }

  @Test
  public void testChangingFileContent() {
    root.doCreateFile(1, p("file"), "content");
    root.doChangeFileContent(1, "new content");

    assertEquals("new content", root.getEntry(p("file")).getContent());
  }

  @Test
  public void testChangingFileContentAffectsOnlyOneFile() {
    root.doCreateFile(1, p("file1"), "content1");
    root.doCreateFile(2, p("file2"), "content2");

    root.doChangeFileContent(1, "new content");

    assertEquals("new content", root.getEntry(p("file1")).getContent());
    assertEquals("content2", root.getEntry(p("file2")).getContent());
  }

  @Test
  public void testChangingContentOfAnNonExistingFileThrowsException() {
    try {
      root.doChangeFileContent(666, "content");
      fail();
    } catch (LocalVcsException e) {}
  }

  @Test
  public void testRenamingFiles() {
    root.doCreateFile(1, p("file"), "content");

    root.doRename(1, "new file");

    assertFalse(root.hasEntry(p("file")));
    assertTrue(root.hasEntry(p("new file")));

    assertEquals("content", root.getEntry(p("new file")).getContent());
  }

  @Test
  public void testRenamingOfAnNonExistingFileThrowsException() {
    try {
      root.doRename(666, "new file");
      fail();
    } catch (LocalVcsException e) {}
  }

  @Test
  public void testRenamingFileToAnExistingFileNameThrowsException() {
    root.doCreateFile(1, p("file1"), null);
    root.doCreateFile(2, p("file2"), null);

    try {
      root.doRename(1, "file2");
      fail();
    } catch (LocalVcsException e) {}
  }

  @Test
  public void testRenamingFileToSameNameDoesNothing() {
    root.doCreateFile(1, p("file"), "content");

    root.doRename(1, "file");

    assertTrue(root.hasEntry(p("file")));
    assertEquals("content", root.getEntry(p("file")).getContent());
  }

  @Test
  public void testDeletingFiles() {
    root.doCreateFile(1, p("file"), null);

    root.doDelete(1);

    assertFalse(root.hasEntry(p("file")));
  }

  @Test
  public void testDeletingOnlyOneFile() {
    root.doCreateFile(1, p("file1"), null);
    root.doCreateFile(2, p("file2"), null);

    root.doDelete(2);

    assertTrue(root.hasEntry(p("file1")));
    assertFalse(root.hasEntry(p("file2")));
  }

  @Test
  public void testDeletingOfAnNonExistingFileThrowsException() {
    try {
      root.doDelete(999);
      fail();
    } catch (LocalVcsException e) {}
  }
}
