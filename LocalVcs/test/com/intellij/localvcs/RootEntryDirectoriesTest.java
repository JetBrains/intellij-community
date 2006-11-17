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
    root.doCreateDirectory(1, p("dir"));
    root.doCreateFile(2, p("dir/file"), "content");

    root.doChangeFileContent(p("dir/file"), "new content");

    assertEquals("new content", root.getEntry(p("dir/file")).getContent());
  }

  @Test
  public void testRenamingDirectories() {
    root.doCreateDirectory(1, p("dir"));

    root.doRename(p("dir"), "new dir");

    assertTrue(root.hasEntry(p("new dir")));
    assertFalse(root.hasEntry(p("dir")));
  }

  @Test
  public void testRenamingFilesUnderDirectory() {
    root.doCreateDirectory(1, p("dir"));
    root.doCreateFile(2, p("dir/file"), "content");

    root.doRename(p("dir/file"), "new file");

    assertFalse(root.hasEntry(p("dir/file")));
    assertTrue(root.hasEntry(p("dir/new file")));

    assertEquals("content", root.getEntry(p("dir/new file")).getContent());
  }

  // todo maybe replace LocalVCSEception with assertions? 

  @Test
  public void testRenamingFilesUnderDirectoryToExistingNameThrowsException() {
    root.doCreateDirectory(1, p("dir"));
    root.doCreateFile(2, p("dir/file1"), null);
    root.doCreateFile(3, p("dir/file2"), null);

    try {
      root.doRename(p("dir/file1"), "file2");
      fail();
    } catch (LocalVcsException e) { }
  }

  @Test
  public void testRenamingSubdirectories() {
    root.doCreateDirectory(1, p("dir1"));
    root.doCreateDirectory(2, p("dir1/dir2"));

    root.doRename(p("dir1/dir2"), "new dir");

    assertTrue(root.hasEntry(p("dir1/new dir")));
    assertFalse(root.hasEntry(p("dir1/dir2")));
  }

  @Test
  public void testRenamingDirectoryWithContent() {
    root.doCreateDirectory(1, p("dir"));
    root.doCreateFile(2, p("dir/file"), null);

    root.doRename(p("dir"), "new dir");

    assertTrue(root.hasEntry(p("new dir")));
    assertTrue(root.hasEntry(p("new dir/file")));

    assertFalse(root.hasEntry(p("dir")));
    assertFalse(root.hasEntry(p("dir/file")));
  }

  @Test
  public void testRenamingDirectoryToExistingFileNameThrowsException() {
    root.doCreateDirectory(1, p("dir1"));
    root.doCreateDirectory(2, p("dir1/dir2"));
    root.doCreateFile(3, p("dir1/file"), null);

    try {
      root.doRename(p("dir1/dir2"), "file");
      fail();
    } catch (LocalVcsException e) {}
  }

  @Test
  public void testMovingFilesBetweenDirectories() {
    root.doCreateDirectory(1, p("dir1"));
    root.doCreateDirectory(2, p("dir2"));
    root.doCreateFile(3, p("dir1/file"), "content");

    root.doMove(p("dir1/file"), p("dir2"));

    assertTrue(root.hasEntry(p("dir2/file")));
    assertFalse(root.hasEntry(p("dir1/file")));

    assertEquals("content", root.getEntry(p("dir2/file")).getContent());
  }

  @Test
  public void testMovingDirectories() {
    root.doCreateDirectory(1, p("root1"));
    root.doCreateDirectory(2, p("root2"));
    root.doCreateDirectory(3, p("root1/dir"));
    root.doCreateFile(4, p("root1/dir/file"), null);

    root.doMove(p("root1/dir"), p("root2"));

    assertTrue(root.hasEntry(p("root2/dir")));
    assertTrue(root.hasEntry(p("root2/dir/file")));

    assertFalse(root.hasEntry(p("root1/dir")));
  }

  @Test
  public void testMovingEntryFromRootToDirectory() {
    root.doCreateDirectory(1, p("dir"));
    root.doCreateFile(2, p("file"), null);

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
    root.doCreateDirectory(1, p("dir1"));
    root.doCreateDirectory(2, p("dir1/dir2"));

    root.doCreateFile(3, p("dir1/file1"), null);
    root.doCreateFile(4, p("dir1/dir2/file2"), null);

    root.doMove(p("dir1/file1"), p("dir1/dir2"));
    root.doMove(p("dir1/dir2/file2"), p("dir1"));

    assertTrue(root.hasEntry(p("dir1/file2")));
    assertTrue(root.hasEntry(p("dir1/dir2/file1")));
  }

  @Test
  public void testMovingDirectoryToItsChildThrowsException() {
    root.doCreateDirectory(1, p("dir1"));
    root.doCreateDirectory(2, p("dir1/dir2"));

    try {
      root.doMove(p("dir1"), p("dir1/dir2"));
      fail();
    } catch (LocalVcsException e) {}
  }

  @Test
  public void testMovingEntryToNotADirectoryThrowsException() {
    root.doCreateFile(1, p("file1"), null);
    root.doCreateFile(2, p("file2"), null);

    try {
      root.doMove(p("file1"), p("file1/file2"));
      fail();
    } catch (LocalVcsException e) {}
  }

  @Test
  public void testMovingEntryToSameDirectory() {
    root.doCreateDirectory(1, p("dir"));
    root.doCreateFile(2, p("dir/file"), null);

    root.doMove(p("dir/file"), p("dir"));

    assertTrue(root.hasEntry(p("dir/file")));
  }

  @Test
  public void testDeletingDirectory() {
    root.doCreateDirectory(1, p("dir"));
    assertTrue(root.hasEntry(p("dir")));

    root.doDelete(p("dir"));
    assertFalse(root.hasEntry(p("dir")));
  }

  @Test
  public void testDeletingSubdirectory() {
    root.doCreateDirectory(1, p("dir1"));
    root.doCreateDirectory(2, p("dir1/dir2"));

    assertTrue(root.hasEntry(p("dir1")));
    assertTrue(root.hasEntry(p("dir1/dir2")));

    root.doDelete(p("dir1/dir2"));
    assertFalse(root.hasEntry(p("dir1/dir2")));

    assertTrue(root.hasEntry(p("dir1")));
  }

  @Test
  public void testDeletingDirectoryWithContent() {
    root.doCreateDirectory(1, p("dir1"));
    root.doCreateDirectory(2, p("dir1/dir2"));

    root.doDelete(p("dir1"));

    assertFalse(root.hasEntry(p("dir1/dir2")));
    assertFalse(root.hasEntry(p("dir1")));
  }

  @Test
  public void testDeletingFilesUnderDirectory() {
    root.doCreateDirectory(1, p("dir"));
    root.doCreateFile(2, p("dir/file"), "");
    assertTrue(root.hasEntry(p("dir/file")));

    root.doDelete(p("dir/file"));
    assertFalse(root.hasEntry(p("dir/file")));
  }
}
