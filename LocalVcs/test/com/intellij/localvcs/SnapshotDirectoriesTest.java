package com.intellij.localvcs;

import org.junit.Test;

public class SnapshotDirectoriesTest extends TestCase {
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
  public void testCreatingParentDirectoriesDirectory() {
    //Snapshot s = new Snapshot();
    //s.doCreateDirectory(fn("dir1/dir2"));
    //
    //assertTrue(s.hasRevision(fn("dir1")));
    //assertTrue(s.hasRevision(fn("dir1/dir2")));
  }
}
