package com.intellij.localvcs;

import org.junit.Test;

public class SnapshotDirectoriesTest extends SnapshotTestCase {
  // todo test boundary conditions
  @Test
  public void testCeatingDirectory() {
    assertFalse(s.hasEntry(p("dir")));

    s.doCreateDirectory(p("dir"));
    assertTrue(s.hasEntry(p("dir")));
    assertEquals(DirectoryEntry.class, s.getEntry(p("dir")).getClass());
    assertTrue(s.getEntry(p("dir")).getChildren().isEmpty());
  }

  @Test
  public void testFilesUnderDirectory() {
    s.doCreateDirectory(p("dir"));
    s.doCreateFile(p("dir/file"), "");

    assertTrue(s.hasEntry(p("dir")));
    assertTrue(s.hasEntry(p("dir/file")));

    Entry dir = s.getEntry(p("dir"));
    Entry file = s.getEntry(p("dir/file"));

    assertEquals(1, dir.getChildren().size());
    assertSame(file, dir.getChildren().get(0));
    assertSame(dir, file.getParent());
  }

  @Test
  public void testCreatingChildredForNonExistingDirectoryThrowsException() {
    try {
      s.doCreateFile(p("dir/file"), "");
      fail();
    } catch (LocalVcsException e) { }

    try {
      s.doCreateDirectory(p("dir1/dir2"));
      fail();
    } catch (LocalVcsException e) { }
  }

  @Test
  public void testCreatingChildredForFileThrowsException() {
    s.doCreateFile(p("file"), null);
    try {
      s.doCreateFile(p("file/child"), null);
      fail();
    } catch (LocalVcsException e) {}
  }

  @Test
  public void teateCreateingDirectoryWithExistedNameThrowsException() {
    s.doCreateFile(p("name1"), null);
    s.doCreateDirectory(p("name2"));

    try {
      s.doCreateDirectory(p("name1"));
      fail();
    } catch (LocalVcsException e) {}

    try {
      s.doCreateDirectory(p("name2"));
      fail();
    } catch (LocalVcsException e) {}
  }

  @Test
  public void testDeletingDirectory() {
    s.doCreateDirectory(p("dir"));
    assertTrue(s.hasEntry(p("dir")));

    s.doDelete(p("dir"));
    assertFalse(s.hasEntry(p("dir")));
  }

  @Test
  public void testDeletingSubdirectory() {
    s.doCreateDirectory(p("dir1"));
    s.doCreateDirectory(p("dir1/dir2"));

    assertTrue(s.hasEntry(p("dir1")));
    assertTrue(s.hasEntry(p("dir1/dir2")));

    s.doDelete(p("dir1/dir2"));
    assertFalse(s.hasEntry(p("dir1/dir2")));

    assertTrue(s.hasEntry(p("dir1")));
  }

  @Test
  public void testDeletingDirectoryWithContent() {
    s.doCreateDirectory(p("dir1"));
    s.doCreateDirectory(p("dir1/dir2"));
    s.doDelete(p("dir1"));

    assertFalse(s.hasEntry(p("dir1/dir2")));
    assertFalse(s.hasEntry(p("dir1")));
  }

  @Test
  public void testDeletingFilesUnderDirectory() {
    s.doCreateDirectory(p("dir"));
    s.doCreateFile(p("dir/file"), "");
    assertTrue(s.hasEntry(p("dir/file")));

    s.doDelete(p("dir/file"));
    assertFalse(s.hasEntry(p("dir/file")));
  }

  @Test(expected = LocalVcsException.class)
  public void testDeletingUnknownDirectoryThrowsException() {
    s.doDelete(p("unknown dir"));
  }

  @Test
  public void testRenamingFilesUnderDirectory() {
    s.doCreateDirectory(p("dir"));
    s.doCreateFile(p("dir/file"), "content");

    s.doRename(p("dir/file"), "new file");

    assertFalse(s.hasEntry(p("dir/file")));
    assertTrue(s.hasEntry(p("dir/new file")));

    assertEquals("content", s.getEntry(p("dir/new file")).getContent());
  }

  @Test
  public void testRenamingSubdirectories() {
    s.doCreateDirectory(p("dir1"));
    s.doCreateDirectory(p("dir1/dir2"));
    s.doCreateFile(p("dir1/dir2/file"), null);

    s.doRename(p("dir1/dir2"), "new dir");

    assertFalse(s.hasEntry(p("dir1/dir2")));
    assertFalse(s.hasEntry(p("dir1/dir2/file")));

    assertTrue(s.hasEntry(p("dir1/new dir")));
    assertTrue(s.hasEntry(p("dir1/new dir/file")));
  }

  @Test
  public void testRenamingDirectoryToExistingFileNameThrowsException() {
    s.doCreateDirectory(p("dir1"));
    s.doCreateDirectory(p("dir1/dir2"));
    s.doCreateFile(p("dir1/file"), null);

    try {
      s.doRename(p("dir1/dir2"), "file");
      fail();
    } catch (LocalVcsException e) {}
  }

  @Test
  public void testApplyingAndRevertingDirectoryCreation() {
    s = s.apply(cs(new CreateDirectoryChange(p("dir"))));
    assertTrue(s.hasEntry(p("dir")));

    s = s.revert();
    assertFalse(s.hasEntry(p("dir")));
  }

  @Test
  public void testApplyingAndRevertingFileCreationUnderDirectory() {
    s = s.apply(cs(new CreateDirectoryChange(p("dir"))));
    s = s.apply(cs(new CreateFileChange(p("dir/file"), "")));

    assertTrue(s.hasEntry(p("dir/file")));

    s = s.revert();
    assertFalse(s.hasEntry(p("dir/file")));
    assertTrue(s.hasEntry(p("dir")));
  }
}
