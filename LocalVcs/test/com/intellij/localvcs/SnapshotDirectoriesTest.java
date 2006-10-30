package com.intellij.localvcs;

import org.junit.Test;

public class SnapshotDirectoriesTest extends TestCase {
  // todo test boundary conditions

  @Test
  public void testCeatingDirectory() {
    Snapshot s = new Snapshot();
    assertFalse(s.hasRevision(fn("dir")));

    s.doCreateDirectory(fn("dir"));
    assertTrue(s.hasRevision(fn("dir")));
    assertEquals(DirectoryRevision.class, s.getRevision(fn("dir")).getClass());
    assertTrue(s.getRevision(fn("dir")).getChildren().isEmpty());
  }

  @Test
  public void testFilesUnderDirectory() {
    Snapshot s = new Snapshot();
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
  public void testCreatingFileUnderNonExistingDirectory() {
    Snapshot s = new Snapshot();
    s.doCreateFile(fn("dir/file"), "");

    assertTrue(s.hasRevision(fn("dir")));
    assertTrue(s.hasRevision(fn("dir/file")));

    Revision dir = s.getRevision(fn("dir"));
    Revision file = s.getRevision(fn("dir/file"));

    assertEquals(1, dir.getChildren().size());
    assertSame(file, dir.getChildren().get(0));
  }

  @Test
  public void testCreatingParentDirectoryForNewDirectory() {
    Snapshot s = new Snapshot();
    s.doCreateDirectory(fn("dir1/dir2"));

    assertTrue(s.hasRevision(fn("dir1")));
    assertTrue(s.hasRevision(fn("dir1/dir2")));

    Revision dir1 = s.getRevision(fn("dir1"));
    Revision dir2 = s.getRevision(fn("dir1/dir2"));

    assertEquals(1, dir1.getChildren().size());
    assertSame(dir2, dir1.getChildren().get(0));
  }

  @Test
  public void testCreatingAllParentDirectories() {
    Snapshot s = new Snapshot();
    s.doCreateFile(fn("dir1/dir2/file"), "");

    assertTrue(s.hasRevision(fn("dir1")));
    assertTrue(s.hasRevision(fn("dir1/dir2")));
    assertTrue(s.hasRevision(fn("dir1/dir2/file")));
  }

  @Test
  public void testDeletingDirectory() {
    Snapshot s = new Snapshot();

    s.doCreateDirectory(fn("dir"));
    assertTrue(s.hasRevision(fn("dir")));

    s.doDelete(fn("dir"));
    assertFalse(s.hasRevision(fn("dir")));
  }

  @Test
  public void testDeletingSubdirectory() {
    Snapshot s = new Snapshot();

    s.doCreateDirectory(fn("dir1/dir2"));
    assertTrue(s.hasRevision(fn("dir1")));
    assertTrue(s.hasRevision(fn("dir1/dir2")));

    s.doDelete(fn("dir1/dir2"));
    assertFalse(s.hasRevision(fn("dir1/dir2")));

    assertTrue(s.hasRevision(fn("dir1")));
  }

  @Test
  public void testDeletingDirectoryWithContent() {
    Snapshot s = new Snapshot();

    s.doCreateDirectory(fn("dir1/dir2"));
    s.doDelete(fn("dir1"));

    assertFalse(s.hasRevision(fn("dir1/dir2")));
    assertFalse(s.hasRevision(fn("dir1")));
  }

  @Test
  public void testDeletingFilesUnderDirectory() {
    Snapshot s = new Snapshot();

    s.doCreateFile(fn("dir/file"), "");
    assertTrue(s.hasRevision(fn("dir/file")));

    s.doDelete(fn("dir/file"));
    assertFalse(s.hasRevision(fn("dir/file")));
  }

  //@Test
  //public void testApplyingRevertingDirectoryCreation() {
  //  Snapshot s = new Snapshot();
  //
  //  s = s.apply(cs(new CreateDirectoryChange(fn("dir"))));
  //  assertTrue(s.hasRevision(fn("dir")));
  //
  //  s = s.revert();
  //  assertFalse(s.hasRevision(fn("dir")));
  //}
}
