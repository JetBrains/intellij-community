package com.intellij.localvcs.core.changes;

import com.intellij.localvcs.core.LocalVcsTestCase;
import com.intellij.localvcs.core.tree.DirectoryEntry;
import com.intellij.localvcs.core.tree.Entry;
import com.intellij.localvcs.core.tree.RootEntry;
import org.junit.Test;

public class RootEntryDirectoriesTest extends LocalVcsTestCase {
  private Entry root = new RootEntry();

  @Test
  public void testCeatingDirectory() {
    assertFalse(root.hasEntry("dir"));

    createDirectory(root, "dir");
    assertTrue(root.hasEntry("dir"));

    Entry e = root.getEntry("dir");

    assertEquals(DirectoryEntry.class, e.getClass());
    assertTrue(e.getChildren().isEmpty());
  }

  @Test
  public void testCreatingFilesUnderDirectory() {
    createDirectory(root, "dir");
    createFile(root, "dir/file", null, -1);

    assertTrue(root.hasEntry("dir"));
    assertTrue(root.hasEntry("dir/file"));

    Entry dir = root.getEntry("dir");
    Entry file = root.getEntry("dir/file");

    assertEquals(1, dir.getChildren().size());
    assertSame(file, dir.getChildren().get(0));
    assertSame(dir, file.getParent());
  }

  @Test
  public void testCreatingFilesUnderNonExistingDirectoryThrowsException() {
    try {
      createFile(root, "dir/file", null, -1);
      fail();
    }
    catch (RuntimeException e) {
    }

    try {
      createFile(root, "dir1/dir2", null, -1);
      fail();
    }
    catch (RuntimeException e) {
    }
  }

  @Test
  public void testChangingFileContentUnderDirectory() {
    createDirectory(root, "dir");
    createFile(root, "dir/file", c("content"), -1);

    changeFileContent(root, "dir/file", c("new content"), -1);

    assertEquals(c("new content"), root.getEntry("dir/file").getContent());
  }

  @Test
  public void testRenamingDirectories() {
    createFile(root, "dir", null, -1);

    rename(root, "dir", "new dir");

    assertTrue(root.hasEntry("new dir"));
    assertFalse(root.hasEntry("dir"));
  }

  @Test
  public void testRenamingFilesUnderDirectory() {
    createDirectory(root, "dir");
    createFile(root, "dir/file", c("content"), -1);

    rename(root, "dir/file", "new file");

    assertFalse(root.hasEntry("dir/file"));
    assertTrue(root.hasEntry("dir/new file"));

    assertEquals(c("content"), root.getEntry("dir/new file").getContent());
  }

  @Test
  public void testRenamingFilesUnderDirectoryToExistingNameThrowsException() {
    createDirectory(root, "dir");
    createFile(root, "dir/file1", null, -1);
    createFile(root, "dir/file2", null, -1);

    try {
      rename(root, "dir/file1", "file2");
      fail();
    }
    catch (RuntimeException e) {
    }
  }

  @Test
  public void testRenamingSubdirectories() {
    createDirectory(root, "dir1");
    createFile(root, "dir1/dir2", null, -1);

    rename(root, "dir1/dir2", "new dir");

    assertTrue(root.hasEntry("dir1/new dir"));
    assertFalse(root.hasEntry("dir1/dir2"));
  }

  @Test
  public void testRenamingDirectoryWithContent() {
    createDirectory(root, "dir");
    createFile(root, "dir/file", null, -1);

    rename(root, "dir", "new dir");

    assertTrue(root.hasEntry("new dir"));
    assertTrue(root.hasEntry("new dir/file"));

    assertFalse(root.hasEntry("dir"));
    assertFalse(root.hasEntry("dir/file"));
  }

  @Test
  public void testRenamingDirectoryToExistingFileNameThrowsException() {
    createDirectory(root, "dir1");
    createDirectory(root, "dir1/dir2");
    createFile(root, "dir1/file", null, -1);

    try {
      rename(root, "dir1/dir2", "file");
      fail();
    }
    catch (RuntimeException e) {
    }
  }

  @Test
  public void testMovingFilesBetweenDirectories() {
    createDirectory(root, "dir1");
    createDirectory(root, "dir2");
    createFile(root, "dir1/file", c("content"), -1);

    move(root, "dir1/file", "dir2");

    assertTrue(root.hasEntry("dir2/file"));
    assertFalse(root.hasEntry("dir1/file"));

    Entry e = root.getEntry("dir2/file");
    assertEquals(c("content"), e.getContent());
  }

  @Test
  public void testMovingDirectories() {
    createDirectory(root, "root1");
    createDirectory(root, "root2");
    createDirectory(root, "root1/dir");
    createFile(root, "root1/dir/file", null, -1);

    move(root, "root1/dir", "root2");

    assertTrue(root.hasEntry("root2/dir"));
    assertTrue(root.hasEntry("root2/dir/file"));

    assertFalse(root.hasEntry("root1/dir"));
  }

  @Test
  public void testDoesNotChangeTimestampOfContentWhenMoving() {
    createDirectory(root, "dir1");
    createDirectory(root, "dir2");
    createFile(root, "dir1/file", null, 99L);

    move(root, "dir1", "dir2");

    assertEquals(99L, root.getEntry("dir2/dir1/file").getTimestamp());
  }

  @Test
  public void testMovingEntryFromRootToDirectory() {
    createDirectory(root, "dir");
    createFile(root, "file", null, -1);

    move(root, "file", "dir");

    assertTrue(root.hasEntry("dir/file"));
    assertFalse(root.hasEntry("file"));
  }

  @Test
  public void testMovingEntriesToAnotherLevelInTree() {
    createDirectory(root, "dir1");
    createDirectory(root, "dir1/dir2");

    createFile(root, "dir1/file1", null, -1);
    createFile(root, "dir1/dir2/file2", null, -1);

    move(root, "dir1/file1", "dir1/dir2");
    move(root, "dir1/dir2/file2", "dir1");

    assertTrue(root.hasEntry("dir1/file2"));
    assertTrue(root.hasEntry("dir1/dir2/file1"));
  }

  @Test
  public void testMovingDirectoryToItsChildThrowsException() {
    createDirectory(root, "dir1");
    createDirectory(root, "dir1/dir2");

    try {
      move(root, "dir1", "dir1/dir2");
      fail();
    }
    catch (RuntimeException e) {
    }
  }

  @Test
  public void testMovingEntryToNotADirectoryThrowsException() {
    createFile(root, "file1", null, -1);
    createFile(root, "file2", null, -1);

    try {
      move(root, "file1", "file1/file2");
      fail();
    }
    catch (RuntimeException e) {
    }
  }

  @Test
  public void testMovingEntryToSameDirectory() {
    createDirectory(root, "dir");
    createFile(root, "dir/file", null, -1);

    move(root, "dir/file", "dir");

    assertTrue(root.hasEntry("dir/file"));
  }

  @Test
  public void testDeletingDirectory() {
    createDirectory(root, "dir");
    assertTrue(root.hasEntry("dir"));

    delete(root, "dir");
    assertFalse(root.hasEntry("dir"));
  }

  @Test
  public void testDeletingSubdirectory() {
    createDirectory(root, "dir1");
    createDirectory(root, "dir1/dir2");

    assertTrue(root.hasEntry("dir1"));
    assertTrue(root.hasEntry("dir1/dir2"));

    delete(root, "dir1/dir2");
    assertFalse(root.hasEntry("dir1/dir2"));

    assertTrue(root.hasEntry("dir1"));
  }

  @Test
  public void testDeletingDirectoryWithContent() {
    createDirectory(root, "dir1");
    createDirectory(root, "dir1/dir2");

    delete(root, "dir1");

    assertFalse(root.hasEntry("dir1/dir2"));
    assertFalse(root.hasEntry("dir1"));
  }

  @Test
  public void testDeletingFilesUnderDirectory() {
    createDirectory(root, "dir");
    createFile(root, "dir/file", null, -1);
    assertTrue(root.hasEntry("dir/file"));

    delete(root, "dir/file");
    assertFalse(root.hasEntry("dir/file"));
  }
}
