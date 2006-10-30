package com.intellij.localvcs;

import org.junit.Before;
import org.junit.Test;

public class SnapshotDirectoriesTest extends TestCase {
  // todo test boundary conditions
  private Snapshot s;

  @Before
  public void setUp() {
    s = new Snapshot();
  }

  @Test
  public void testCeatingDirectory() {
    assertFalse(s.hasRevision(fn("dir")));

    s.doCreateDirectory(fn("dir"));
    assertTrue(s.hasRevision(fn("dir")));
    assertEquals(DirectoryRevision.class, s.getRevision(fn("dir")).getClass());
    assertTrue(s.getRevision(fn("dir")).getChildren().isEmpty());
  }

  @Test
  public void testFilesUnderDirectory() {
    s.doCreateDirectory(fn("dir"));
    s.doCreateFile(fn("dir/file"), "");

    assertTrue(s.hasRevision(fn("dir")));
    assertTrue(s.hasRevision(fn("dir/file")));

    Revision dir = s.getRevision(fn("dir"));
    Revision file = s.getRevision(fn("dir/file"));

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
    assertTrue(s.hasRevision(fn("dir")));

    s.doDelete(fn("dir"));
    assertFalse(s.hasRevision(fn("dir")));
  }

  @Test
  public void testDeletingSubdirectory() {
    s.doCreateDirectory(fn("dir1"));
    s.doCreateDirectory(fn("dir1/dir2"));

    assertTrue(s.hasRevision(fn("dir1")));
    assertTrue(s.hasRevision(fn("dir1/dir2")));

    s.doDelete(fn("dir1/dir2"));
    assertFalse(s.hasRevision(fn("dir1/dir2")));

    assertTrue(s.hasRevision(fn("dir1")));
  }

  @Test
  public void testDeletingDirectoryWithContent() {
    s.doCreateDirectory(fn("dir1"));
    s.doCreateDirectory(fn("dir1/dir2"));
    s.doDelete(fn("dir1"));

    assertFalse(s.hasRevision(fn("dir1/dir2")));
    assertFalse(s.hasRevision(fn("dir1")));
  }

  @Test
  public void testDeletingFilesUnderDirectory() {
    s.doCreateDirectory(fn("dir"));
    s.doCreateFile(fn("dir/file"), "");
    assertTrue(s.hasRevision(fn("dir/file")));

    s.doDelete(fn("dir/file"));
    assertFalse(s.hasRevision(fn("dir/file")));
  }

  @Test
  public void testRenamingFilesUnderDirectory() {
    s.doCreateDirectory(fn("dir"));
    s.doCreateFile(fn("dir/file"), "content");

    s.doRename(fn("dir/file"), "new file");

    assertFalse(s.hasRevision(fn("dir/file")));
    assertTrue(s.hasRevision(fn("dir/new file")));

    assertEquals("content", s.getRevision(fn("dir/new file")).getContent());
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
    assertTrue(s.hasRevision(fn("dir")));

    s = s.revert();
    assertFalse(s.hasRevision(fn("dir")));
  }

  @Test
  public void testApplyingAndRevertingFileCreationUnderDirectory() {
    s = s.apply(cs(new CreateDirectoryChange(fn("dir"))));
    s = s.apply(cs(new CreateFileChange(fn("dir/file"), "")));

    assertTrue(s.hasRevision(fn("dir/file")));

    s = s.revert();
    assertFalse(s.hasRevision(fn("dir/file")));
    assertTrue(s.hasRevision(fn("dir")));
  }
}
