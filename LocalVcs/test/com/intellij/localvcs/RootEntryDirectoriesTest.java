package com.intellij.localvcs;

import org.junit.Test;

public class RootEntryDirectoriesTest extends TestCase {
  private RootEntry root = new RootEntry();

  @Test
  public void testCeatingDirectory() {
    assertFalse(root.hasEntry(p("dir")));

    root.doCreateDirectory(null, p("dir"));
    assertTrue(root.hasEntry(p("dir")));
    assertEquals(DirectoryEntry.class, root.getEntry(p("dir")).getClass());
    assertTrue(root.getEntry(p("dir")).getChildren().isEmpty());
  }

  @Test
  public void testCreatingFilesUnderDirectory() {
    root.doCreateDirectory(null, p("dir"));
    root.doCreateFile(null, p("dir/file"), "");

    assertTrue(root.hasEntry(p("dir")));
    assertTrue(root.hasEntry(p("dir/file")));

    Entry dir = root.getEntry(p("dir"));
    Entry file = root.getEntry(p("dir/file"));

    assertEquals(1, dir.getChildren().size());
    assertSame(file, dir.getChildren().get(0));
    assertSame(dir, file.getParent());
  }

  @Test
  public void testCreatingChildredForNonExistingDirectoryThrowsException() {
    try {
      root.doCreateFile(null, p("dir/file"), "");
      fail();
    } catch (LocalVcsException e) { }

    try {
      root.doCreateDirectory(null, p("dir1/dir2"));
      fail();
    } catch (LocalVcsException e) { }
  }

  @Test
  public void testCreatingChildredForFileThrowsException() {
    root.doCreateFile(null, p("file"), null);
    try {
      root.doCreateFile(null, p("file/child"), null);
      fail();
    } catch (LocalVcsException e) {}
  }

  @Test
  public void testCreateingDirectoryWithExistedNameThrowsException() {
    root.doCreateFile(null, p("name1"), null);
    root.doCreateDirectory(null, p("name2"));

    try {
      root.doCreateDirectory(null, p("name1"));
      fail();
    } catch (LocalVcsException e) {}

    try {
      root.doCreateDirectory(null, p("name2"));
      fail();
    } catch (LocalVcsException e) {}
  }

  @Test
  public void testChangingFileContentUnderDirectory() {
    root.doCreateDirectory(null, p("dir"));
    root.doCreateFile(null, p("dir/file"), "content");

    root.doChangeFileContent(p("dir/file"), "new content");

    assertEquals("new content", root.getEntry(p("dir/file")).getContent());
  }

  @Test
  public void testRenamingDirectories() {
    root.doCreateDirectory(null, p("dir"));

    root.doRename(p("dir"), "new dir");

    assertTrue(root.hasEntry(p("new dir")));
    assertFalse(root.hasEntry(p("dir")));
  }

  @Test
  public void testRenamingFilesUnderDirectory() {
    root.doCreateDirectory(null, p("dir"));
    root.doCreateFile(null, p("dir/file"), "content");

    root.doRename(p("dir/file"), "new file");

    assertFalse(root.hasEntry(p("dir/file")));
    assertTrue(root.hasEntry(p("dir/new file")));

    assertEquals("content", root.getEntry(p("dir/new file")).getContent());
  }

  @Test
  public void testRenamingFilesUnderDirectoryToExistingNameThrowsException() {
    root.doCreateDirectory(null, p("dir"));
    root.doCreateFile(null, p("dir/file1"), null);
    root.doCreateFile(null, p("dir/file2"), null);

    try {
      root.doRename(p("dir/file1"), "file2");
      fail();
    } catch (LocalVcsException e) { }
  }

  @Test
  public void testRenamingSubdirectories() {
    root.doCreateDirectory(null, p("dir1"));
    root.doCreateDirectory(null, p("dir1/dir2"));

    root.doRename(p("dir1/dir2"), "new dir");

    assertTrue(root.hasEntry(p("dir1/new dir")));
    assertFalse(root.hasEntry(p("dir1/dir2")));
  }

  @Test
  public void testRenamingDirectoryWithContent() {
    root.doCreateDirectory(null, p("dir"));
    root.doCreateFile(null, p("dir/file"), null);

    root.doRename(p("dir"), "new dir");

    assertTrue(root.hasEntry(p("new dir")));
    assertTrue(root.hasEntry(p("new dir/file")));

    assertFalse(root.hasEntry(p("dir")));
    assertFalse(root.hasEntry(p("dir/file")));
  }

  @Test
  public void testRenamingDirectoryToExistingFileNameThrowsException() {
    root.doCreateDirectory(null, p("dir1"));
    root.doCreateDirectory(null, p("dir1/dir2"));
    root.doCreateFile(null, p("dir1/file"), null);

    try {
      root.doRename(p("dir1/dir2"), "file");
      fail();
    } catch (LocalVcsException e) {}
  }

  @Test
  public void testMovingFilesBetweenDirectories() {
    root.doCreateDirectory(null, p("dir1"));
    root.doCreateDirectory(null, p("dir2"));
    root.doCreateFile(null, p("dir1/file"), "content");

    root.doMove(p("dir1/file"), p("dir2"));

    assertTrue(root.hasEntry(p("dir2/file")));
    assertFalse(root.hasEntry(p("dir1/file")));

    assertEquals("content", root.getEntry(p("dir2/file")).getContent());
  }

  @Test
  public void testMovingDirectories() {
    root.doCreateDirectory(null, p("root1"));
    root.doCreateDirectory(null, p("root2"));
    root.doCreateDirectory(null, p("root1/dir"));
    root.doCreateFile(null, p("root1/dir/file"), null);

    root.doMove(p("root1/dir"), p("root2"));

    assertTrue(root.hasEntry(p("root2/dir")));
    assertTrue(root.hasEntry(p("root2/dir/file")));

    assertFalse(root.hasEntry(p("root1/dir")));
  }

  @Test
  public void testMovingEntryFromRootToDirectory() {
    root.doCreateDirectory(null, p("dir"));
    root.doCreateFile(null, p("file"), null);

    root.doMove(p("file"), p("dir"));

    assertTrue(root.hasEntry(p("dir/file")));
    assertFalse(root.hasEntry(p("file")));
  }

  @Test
  public void testMovingEntryFromDirectoryToRoot() {
    root.doCreateDirectory(null, p("dir"));
    root.doCreateFile(null, p("dir/file"), null);

    // todo move to where??? shold we support this case?
    //s.doMove(p("file"), p(""));
    //
    //assertTrue(s.hasEntry(p("file")));
    //assertFalse(s.hasEntry(p("dir/file")));
  }

  @Test
  public void testMovingEntriesToAnotherLevelInTree() {
    root.doCreateDirectory(null, p("dir1"));
    root.doCreateDirectory(null, p("dir1/dir2"));

    root.doCreateFile(null, p("dir1/file1"), null);
    root.doCreateFile(null, p("dir1/dir2/file2"), null);

    root.doMove(p("dir1/file1"), p("dir1/dir2"));
    root.doMove(p("dir1/dir2/file2"), p("dir1"));

    assertTrue(root.hasEntry(p("dir1/file2")));
    assertTrue(root.hasEntry(p("dir1/dir2/file1")));
  }

  @Test
  public void testMovingDirectoryToItsChildThrowsException() {
    root.doCreateDirectory(null, p("dir1"));
    root.doCreateDirectory(null, p("dir1/dir2"));

    try {
      root.doMove(p("dir1"), p("dir1/dir2"));
      fail();
    } catch (LocalVcsException e) {}
  }

  @Test
  public void testMovingEntryToNotADirectoryThrowsException() {
    root.doCreateFile(null, p("file1"), null);
    root.doCreateFile(null, p("file2"), null);

    try {
      root.doMove(p("file1"), p("file1/file2"));
      fail();
    } catch (LocalVcsException e) {}
  }

  @Test
  public void testMovingEntryToSameDirectory() {
    root.doCreateDirectory(null, p("dir"));
    root.doCreateFile(null, p("dir/file"), null);

    root.doMove(p("dir/file"), p("dir"));

    assertTrue(root.hasEntry(p("dir/file")));
  }

  @Test
  public void testDeletingDirectory() {
    root.doCreateDirectory(null, p("dir"));
    assertTrue(root.hasEntry(p("dir")));

    root.doDelete(p("dir"));
    assertFalse(root.hasEntry(p("dir")));
  }

  @Test
  public void testDeletingSubdirectory() {
    root.doCreateDirectory(null, p("dir1"));
    root.doCreateDirectory(null, p("dir1/dir2"));

    assertTrue(root.hasEntry(p("dir1")));
    assertTrue(root.hasEntry(p("dir1/dir2")));

    root.doDelete(p("dir1/dir2"));
    assertFalse(root.hasEntry(p("dir1/dir2")));

    assertTrue(root.hasEntry(p("dir1")));
  }

  @Test
  public void testDeletingDirectoryWithContent() {
    root.doCreateDirectory(null, p("dir1"));
    root.doCreateDirectory(null, p("dir1/dir2"));
    root.doDelete(p("dir1"));

    assertFalse(root.hasEntry(p("dir1/dir2")));
    assertFalse(root.hasEntry(p("dir1")));
  }

  @Test
  public void testDeletingFilesUnderDirectory() {
    root.doCreateDirectory(null, p("dir"));
    root.doCreateFile(null, p("dir/file"), "");
    assertTrue(root.hasEntry(p("dir/file")));

    root.doDelete(p("dir/file"));
    assertFalse(root.hasEntry(p("dir/file")));
  }

  @Test(expected = LocalVcsException.class)
  public void testDeletingUnknownDirectoryThrowsException() {
    root.doDelete(p("unknown dir"));
  }
}
