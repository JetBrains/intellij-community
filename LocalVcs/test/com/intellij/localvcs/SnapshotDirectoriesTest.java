package com.intellij.localvcs;

import org.junit.Test;

public class SnapshotDirectoriesTest extends TestCase {
  // todo should we test boundary conditions at all?
  @Test
  public void testCeatingDirectory() {
    assertFalse(s.hasEntry(p("dir")));

    s.doCreateDirectory(p("dir"));
    assertTrue(s.hasEntry(p("dir")));
    assertEquals(DirectoryEntry.class, s.getEntry(p("dir")).getClass());
    assertTrue(s.getEntry(p("dir")).getChildren().isEmpty());
  }

  @Test
  public void testCreatingFilesUnderDirectory() {
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
  public void testCreateingDirectoryWithExistedNameThrowsException() {
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
  public void testRenamingDirectories() {
    s.doCreateDirectory(p("dir"));

    s.doRename(p("dir"), "new dir");

    assertTrue(s.hasEntry(p("new dir")));
    assertFalse(s.hasEntry(p("dir")));
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

    s.doRename(p("dir1/dir2"), "new dir");

    assertTrue(s.hasEntry(p("dir1/new dir")));
    assertFalse(s.hasEntry(p("dir1/dir2")));
  }

  @Test
  public void testRenamingDirectoryWithContent() {
    s.doCreateDirectory(p("dir"));
    s.doCreateFile(p("dir/file"), null);

    s.doRename(p("dir"), "new dir");

    assertTrue(s.hasEntry(p("new dir")));
    assertTrue(s.hasEntry(p("new dir/file")));

    assertFalse(s.hasEntry(p("dir")));
    assertFalse(s.hasEntry(p("dir/file")));
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

  // todo is renaming and moving are the same things?
  // todo should we support both renaming and moving?
  @Test
  public void testMovingFilesBetweenDirectories() {
    s.doCreateDirectory(p("dir1"));
    s.doCreateDirectory(p("dir2"));
    s.doCreateFile(p("dir1/file"), "content");

    s.doMove(p("dir1/file"), p("dir2"));

    assertTrue(s.hasEntry(p("dir2/file")));
    assertFalse(s.hasEntry(p("dir1/file")));

    assertEquals("content", s.getEntry(p("dir2/file")).getContent());
  }

  @Test
  public void testMovingDirectories() {
    s.doCreateDirectory(p("root1"));
    s.doCreateDirectory(p("root2"));
    s.doCreateDirectory(p("root1/dir"));
    s.doCreateFile(p("root1/dir/file"), null);

    s.doMove(p("root1/dir"), p("root2"));

    assertTrue(s.hasEntry(p("root2/dir")));
    assertTrue(s.hasEntry(p("root2/dir/file")));

    assertFalse(s.hasEntry(p("root1/dir")));
  }

  @Test
  public void testMovingEntryFromRootToDirectory() {
    s.doCreateDirectory(p("dir"));
    s.doCreateFile(p("file"), null);

    s.doMove(p("file"), p("dir"));

    assertTrue(s.hasEntry(p("dir/file")));
    assertFalse(s.hasEntry(p("file")));
  }

  @Test
  public void testMovingEntryFromDirectoryToRoot() {
    s.doCreateDirectory(p("dir"));
    s.doCreateFile(p("dir/file"), null);

    // todo move to where??? shold we support this case?
    //s.doMove(p("file"), p(""));
    //
    //assertTrue(s.hasEntry(p("file")));
    //assertFalse(s.hasEntry(p("dir/file")));
  }

  @Test
  public void testMovingEntriesToAnotherLevelInTree() {
    s.doCreateDirectory(p("dir1"));
    s.doCreateDirectory(p("dir1/dir2"));

    s.doCreateFile(p("dir1/file1"), null);
    s.doCreateFile(p("dir1/dir2/file2"), null);

    s.doMove(p("dir1/file1"), p("dir1/dir2"));
    s.doMove(p("dir1/dir2/file2"), p("dir1"));

    assertTrue(s.hasEntry(p("dir1/file2")));
    assertTrue(s.hasEntry(p("dir1/dir2/file1")));
  }

  @Test
  public void testMovingDirectoryToItsChildThrowsException() {
    s.doCreateDirectory(p("dir1"));
    s.doCreateDirectory(p("dir1/dir2"));

    try {
      s.doMove(p("dir1"), p("dir1/dir2"));
      fail();
    } catch (LocalVcsException e) {}
  }

  @Test
  public void testMovingEntryToNotADirectoryThrowsException() {
    s.doCreateFile(p("file1"), null);
    s.doCreateFile(p("file2"), null);

    try {
      s.doMove(p("file1"), p("file1/file2"));
      fail();
    } catch (LocalVcsException e) {}
  }

  @Test
  public void testMovingEntryToSameDirectory() {
    // todo maybe this test is useless

    s.doCreateDirectory(p("dir"));
    s.doCreateFile(p("dir/file"), null);

    s.doMove(p("dir/file"), p("dir"));

    assertTrue(s.hasEntry(p("dir/file")));
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
}
