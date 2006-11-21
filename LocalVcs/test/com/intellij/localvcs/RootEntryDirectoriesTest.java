package com.intellij.localvcs;

import org.junit.Test;

public class RootEntryDirectoriesTest extends TestCase {
  private RootEntry root = new RootEntry("");

  @Test
  public void testCeatingDirectory() {
    assertFalse(root.hasEntry(p("/dir")));

    root.doCreateDirectory(null, p("/dir"), 89L);
    assertTrue(root.hasEntry(p("/dir")));

    Entry e = root.getEntry(p("/dir"));

    assertEquals(DirectoryEntry.class, e.getClass());
    assertEquals(89L, e.getTimestamp());
    assertTrue(e.getChildren().isEmpty());
  }

  @Test
  public void testCreatingFilesUnderDirectory() {
    root.doCreateDirectory(null, p("/dir"), null);
    root.doCreateFile(null, p("/dir/file"), null, null);

    assertTrue(root.hasEntry(p("/dir")));
    assertTrue(root.hasEntry(p("/dir/file")));

    Entry dir = root.getEntry(p("/dir"));
    Entry file = root.getEntry(p("/dir/file"));

    assertEquals(1, dir.getChildren().size());
    assertSame(file, dir.getChildren().get(0));
    assertSame(dir, file.getParent());
  }

  @Test
  public void testCreatingChildredForNonExistingDirectoryThrowsException() {
    try {
      root.doCreateFile(null, p("/dir/file"), null, null);
      fail();
    }
    catch (LocalVcsException e) {
    }

    try {
      root.doCreateFile(null, p("/dir1/dir2"), null, null);
      fail();
    }
    catch (LocalVcsException e) {
    }
  }

  @Test
  public void testCreatingChildredForFileThrowsException() {
    root.doCreateFile(null, p("/file"), null, null);
    try {
      root.doCreateFile(null, p("/file/child"), null, null);
      fail();
    }
    catch (LocalVcsException e) {
    }
  }

  @Test
  public void testCreatingDirectoryWithExistedNameThrowsException() {
    root.doCreateFile(null, p("/name1"), null, null);
    root.doCreateFile(null, p("/name2"), null, null);

    try {
      root.doCreateFile(null, p("/name1"), null, null);
      fail();
    }
    catch (LocalVcsException e) {
    }

    try {
      root.doCreateFile(null, p("/name2"), null, null);
      fail();
    }
    catch (LocalVcsException e) {
    }
  }

  @Test
  public void testChangingFileContentUnderDirectory() {
    root.doCreateDirectory(1, p("/dir"), null);
    root.doCreateFile(2, p("/dir/file"), "content", null);

    root.doChangeFileContent(p("/dir/file"), "new content", null);

    assertEquals("new content", root.getEntry(p("/dir/file")).getContent());
  }

  @Test
  public void testRenamingDirectories() {
    root.doCreateFile(1, p("/dir"), null, null);

    root.doRename(p("/dir"), "new dir", 18L);

    assertTrue(root.hasEntry(p("/new dir")));
    assertFalse(root.hasEntry(p("/dir")));

    assertEquals(18L, root.getEntry(p("/new dir")).getTimestamp());
  }

  @Test
  public void testRenamingFilesUnderDirectory() {
    root.doCreateDirectory(1, p("/dir"), null);
    root.doCreateFile(2, p("/dir/file"), "content", null);

    root.doRename(p("/dir/file"), "new file", null);

    assertFalse(root.hasEntry(p("/dir/file")));
    assertTrue(root.hasEntry(p("/dir/new file")));

    assertEquals("content", root.getEntry(p("/dir/new file")).getContent());
  }

  // todo maybe replace LocalVCSEception with assertions? 

  @Test
  public void testRenamingFilesUnderDirectoryToExistingNameThrowsException() {
    root.doCreateDirectory(1, p("/dir"), null);
    root.doCreateFile(2, p("/dir/file1"), null, null);
    root.doCreateFile(3, p("/dir/file2"), null, null);

    try {
      root.doRename(p("/dir/file1"), "file2", null);
      fail();
    }
    catch (LocalVcsException e) {
    }
  }

  @Test
  public void testRenamingSubdirectories() {
    root.doCreateDirectory(1, p("/dir1"), null);
    root.doCreateFile(2, p("/dir1/dir2"), null, null);

    root.doRename(p("/dir1/dir2"), "new dir", null);

    assertTrue(root.hasEntry(p("/dir1/new dir")));
    assertFalse(root.hasEntry(p("/dir1/dir2")));
  }

  @Test
  public void testRenamingDirectoryWithContent() {
    root.doCreateDirectory(1, p("/dir"), null);
    root.doCreateFile(2, p("/dir/file"), null, null);

    root.doRename(p("/dir"), "new dir", null);

    assertTrue(root.hasEntry(p("/new dir")));
    assertTrue(root.hasEntry(p("/new dir/file")));

    assertFalse(root.hasEntry(p("/dir")));
    assertFalse(root.hasEntry(p("/dir/file")));
  }

  @Test
  public void testRenamingDirectoryToExistingFileNameThrowsException() {
    root.doCreateDirectory(1, p("/dir1"), null);
    root.doCreateDirectory(2, p("/dir1/dir2"), null);
    root.doCreateFile(3, p("/dir1/file"), null, null);

    try {
      root.doRename(p("/dir1/dir2"), "file", null);
      fail();
    }
    catch (LocalVcsException e) {
    }
  }

  @Test
  public void testMovingFilesBetweenDirectories() {
    root.doCreateDirectory(1, p("/dir1"), null);
    root.doCreateDirectory(2, p("/dir2"), null);
    root.doCreateFile(3, p("/dir1/file"), "content", null);

    root.doMove(p("/dir1/file"), p("/dir2"), 13L);

    assertTrue(root.hasEntry(p("/dir2/file")));
    assertFalse(root.hasEntry(p("/dir1/file")));

    Entry e = root.getEntry(p("/dir2/file"));
    assertEquals("content", e.getContent());
    assertEquals(13L, e.getTimestamp());
  }

  @Test
  public void testMovingDirectories() {
    root.doCreateDirectory(1, p("/root1"), null);
    root.doCreateDirectory(2, p("/root2"), null);
    root.doCreateDirectory(3, p("/root1/dir"), null);
    root.doCreateFile(4, p("/root1/dir/file"), null, null);

    root.doMove(p("/root1/dir"), p("/root2"), 15L);

    assertTrue(root.hasEntry(p("/root2/dir")));
    assertTrue(root.hasEntry(p("/root2/dir/file")));

    assertFalse(root.hasEntry(p("/root1/dir")));

    assertEquals(15L, root.getEntry(p("/root2/dir")).getTimestamp());
  }

  @Test
  public void testDoesNotChangeTimestampOfContentWhenMoving() {
    root.doCreateDirectory(1, p("/dir1"), null);
    root.doCreateDirectory(2, p("/dir2"), null);
    root.doCreateFile(3, p("/dir1/file"), null, 99L);

    root.doMove(p("/dir1"), p("/dir2"), null);

    assertEquals(99L, root.getEntry(p("/dir2/dir1/file")).getTimestamp());
  }

  @Test
  public void testMovingEntryFromRootToDirectory() {
    root.doCreateDirectory(1, p("/dir"), null);
    root.doCreateFile(2, p("/file"), null, null);

    root.doMove(p("/file"), p("/dir"), null);

    assertTrue(root.hasEntry(p("/dir/file")));
    assertFalse(root.hasEntry(p("/file")));
  }

  @Test
  public void testMovingEntryFromDirectoryToRoot() {
    root.setPath("root");

    root.doCreateDirectory(null, p("root/dir"), null);
    root.doCreateFile(null, p("root/dir/file"), null, null);

    root.doMove(p("root/dir/file"), p("root"), null);

    assertTrue(root.hasEntry(p("root/file")));
    assertFalse(root.hasEntry(p("toor/dir/file")));
  }

  @Test
  public void testMovingEntriesToAnotherLevelInTree() {
    root.doCreateDirectory(1, p("/dir1"), null);
    root.doCreateDirectory(2, p("/dir1/dir2"), null);

    root.doCreateFile(3, p("/dir1/file1"), null, null);
    root.doCreateFile(4, p("/dir1/dir2/file2"), null, null);

    root.doMove(p("/dir1/file1"), p("/dir1/dir2"), null);
    root.doMove(p("/dir1/dir2/file2"), p("/dir1"), null);

    assertTrue(root.hasEntry(p("/dir1/file2")));
    assertTrue(root.hasEntry(p("/dir1/dir2/file1")));
  }

  @Test
  public void testMovingDirectoryToItsChildThrowsException() {
    root.doCreateDirectory(1, p("/dir1"), null);
    root.doCreateDirectory(2, p("/dir1/dir2"), null);

    try {
      root.doMove(p("/dir1"), p("/dir1/dir2"), null);
      fail();
    }
    catch (LocalVcsException e) {
    }
  }

  @Test
  public void testMovingEntryToNotADirectoryThrowsException() {
    root.doCreateFile(1, p("/file1"), null, null);
    root.doCreateFile(2, p("/file2"), null, null);

    try {
      root.doMove(p("/file1"), p("/file1/file2"), null);
      fail();
    }
    catch (LocalVcsException e) {
    }
  }

  @Test
  public void testMovingEntryToSameDirectory() {
    root.doCreateDirectory(1, p("/dir"), null);
    root.doCreateFile(2, p("/dir/file"), null, null);

    root.doMove(p("/dir/file"), p("/dir"), null);

    assertTrue(root.hasEntry(p("/dir/file")));
  }

  @Test
  public void testDeletingDirectory() {
    root.doCreateDirectory(1, p("/dir"), null);
    assertTrue(root.hasEntry(p("/dir")));

    root.doDelete(p("/dir"));
    assertFalse(root.hasEntry(p("/dir")));
  }

  @Test
  public void testDeletingSubdirectory() {
    root.doCreateDirectory(1, p("/dir1"), null);
    root.doCreateDirectory(2, p("/dir1/dir2"), null);

    assertTrue(root.hasEntry(p("/dir1")));
    assertTrue(root.hasEntry(p("/dir1/dir2")));

    root.doDelete(p("/dir1/dir2"));
    assertFalse(root.hasEntry(p("/dir1/dir2")));

    assertTrue(root.hasEntry(p("/dir1")));
  }

  @Test
  public void testDeletingDirectoryWithContent() {
    root.doCreateDirectory(1, p("/dir1"), null);
    root.doCreateDirectory(2, p("/dir1/dir2"), null);

    root.doDelete(p("/dir1"));

    assertFalse(root.hasEntry(p("/dir1/dir2")));
    assertFalse(root.hasEntry(p("/dir1")));
  }

  @Test
  public void testDeletingFilesUnderDirectory() {
    root.doCreateDirectory(1, p("/dir"), null);
    root.doCreateFile(2, p("/dir/file"), "", null);
    assertTrue(root.hasEntry(p("/dir/file")));

    root.doDelete(p("/dir/file"));
    assertFalse(root.hasEntry(p("/dir/file")));
  }
}
