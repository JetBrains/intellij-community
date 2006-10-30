package com.intellij.localvcs;

import org.junit.Test;

public class SnapshotDirectoriesTest extends SnapshotTestCase {
  // todo test boundary conditions
  @Test
  public void testCeatingDirectory() {
    assertFalse(s.hasEntry(fn("dir")));

    s.doCreateDirectory(fn("dir"));
    assertTrue(s.hasEntry(fn("dir")));
    assertEquals(DirectoryEntry.class, s.getEntry(fn("dir")).getClass());
    assertTrue(s.getEntry(fn("dir")).getChildren().isEmpty());
  }

  @Test
  public void testFilesUnderDirectory() {
    s.doCreateDirectory(fn("dir"));
    s.doCreateFile(fn("dir/file"), "");

    assertTrue(s.hasEntry(fn("dir")));
    assertTrue(s.hasEntry(fn("dir/file")));

    Entry dir = s.getEntry(fn("dir"));
    Entry file = s.getEntry(fn("dir/file"));

    assertEquals(1, dir.getChildren().size());
    assertSame(file, dir.getChildren().get(0));
    assertSame(dir, file.getParent());
  }

  @Test
  public void testCreatingChildredForNonExistingDirectoryThrowsException() {
    try {
      s.doCreateFile(fn("dir/file"), "");
      fail();
    } catch (LocalVcsException e) { }

    try {
      s.doCreateDirectory(fn("dir1/dir2"));
      fail();
    } catch (LocalVcsException e) { }
  }

  @Test
  public void testDeletingDirectory() {
    s.doCreateDirectory(fn("dir"));
    assertTrue(s.hasEntry(fn("dir")));

    s.doDelete(fn("dir"));
    assertFalse(s.hasEntry(fn("dir")));
  }

  @Test
  public void testDeletingSubdirectory() {
    s.doCreateDirectory(fn("dir1"));
    s.doCreateDirectory(fn("dir1/dir2"));

    assertTrue(s.hasEntry(fn("dir1")));
    assertTrue(s.hasEntry(fn("dir1/dir2")));

    s.doDelete(fn("dir1/dir2"));
    assertFalse(s.hasEntry(fn("dir1/dir2")));

    assertTrue(s.hasEntry(fn("dir1")));
  }

  @Test
  public void testDeletingDirectoryWithContent() {
    s.doCreateDirectory(fn("dir1"));
    s.doCreateDirectory(fn("dir1/dir2"));
    s.doDelete(fn("dir1"));

    assertFalse(s.hasEntry(fn("dir1/dir2")));
    assertFalse(s.hasEntry(fn("dir1")));
  }

  @Test
  public void testDeletingFilesUnderDirectory() {
    s.doCreateDirectory(fn("dir"));
    s.doCreateFile(fn("dir/file"), "");
    assertTrue(s.hasEntry(fn("dir/file")));

    s.doDelete(fn("dir/file"));
    assertFalse(s.hasEntry(fn("dir/file")));
  }

  @Test
  public void testRenamingFilesUnderDirectory() {
    s.doCreateDirectory(fn("dir"));
    s.doCreateFile(fn("dir/file"), "content");

    s.doRename(fn("dir/file"), "new file");

    assertFalse(s.hasEntry(fn("dir/file")));
    assertTrue(s.hasEntry(fn("dir/new file")));

    assertEquals("content", s.getEntry(fn("dir/new file")).getContent());
  }

  @Test
  public void testRenamingSubdirectories() {
    // todo 
    //s.doCreateDirectory(fn("dir1"));
    //s.doCreateDirectory(fn("dir1/dir2"));
    //s.doCreateFile(fn("dir1/dir2/file"), null);
    //
    //s.doRename(fn("dir1/dir2"), fn("new dir"));
    //
    //assertFalse(s.hasRevision(fn("dir1/dir2")));
    //assertFalse(s.hasRevision(fn("dir1/dir2/file")));
    //
    //assertTrue(s.hasRevision(fn("dir1/new dir")));
    //assertTrue(s.hasRevision(fn("dir1/new dir/file")));
  }

  @Test
  public void testApplyingAndRevertingDirectoryCreation() {
    s = s.apply(cs(new CreateDirectoryChange(fn("dir"))));
    assertTrue(s.hasEntry(fn("dir")));

    s = s.revert();
    assertFalse(s.hasEntry(fn("dir")));
  }

  @Test
  public void testApplyingAndRevertingFileCreationUnderDirectory() {
    s = s.apply(cs(new CreateDirectoryChange(fn("dir"))));
    s = s.apply(cs(new CreateFileChange(fn("dir/file"), "")));

    assertTrue(s.hasEntry(fn("dir/file")));

    s = s.revert();
    assertFalse(s.hasEntry(fn("dir/file")));
    assertTrue(s.hasEntry(fn("dir")));
  }
}
