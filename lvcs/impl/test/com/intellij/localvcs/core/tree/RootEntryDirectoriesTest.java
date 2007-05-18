package com.intellij.localvcs.core.tree;

import com.intellij.localvcs.core.LocalVcsTestCase;
import org.junit.Test;

public class RootEntryDirectoriesTest extends LocalVcsTestCase {
  private RootEntry root = new RootEntry();

  @Test
  public void testCeatingDirectory() {
    assertFalse(root.hasEntry("dir"));

    root.createDirectory(-1, "dir");
    assertTrue(root.hasEntry("dir"));

    Entry e = root.getEntry("dir");

    assertEquals(DirectoryEntry.class, e.getClass());
    assertTrue(e.getChildren().isEmpty());
  }

  @Test
  public void testReturningCreatedEntry() {
    Entry e = root.createDirectory(-1, "dir");
    assertSame(e, root.getEntry("dir"));
  }

  @Test
  public void testCreatingFilesUnderDirectory() {
    root.createDirectory(-1, "dir");
    root.createFile(-1, "dir/file", null, -1);

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
      root.createFile(-1, "dir/file", null, -1);
      fail();
    }
    catch (RuntimeException e) {
    }

    try {
      root.createFile(-1, "dir1/dir2", null, -1);
      fail();
    }
    catch (RuntimeException e) {
    }
  }

  @Test
  public void testChangingFileContentUnderDirectory() {
    root.createDirectory(1, "dir");
    root.createFile(2, "dir/file", c("content"), -1);

    root.changeFileContent("dir/file", c("new content"), -1);

    assertEquals(c("new content"), root.getEntry("dir/file").getContent());
  }

  @Test
  public void testRenamingDirectories() {
    root.createFile(1, "dir", null, -1);

    root.rename("dir", "new dir");

    assertTrue(root.hasEntry("new dir"));
    assertFalse(root.hasEntry("dir"));
  }

  @Test
  public void testRenamingFilesUnderDirectory() {
    root.createDirectory(1, "dir");
    root.createFile(2, "dir/file", c("content"), -1);

    root.rename("dir/file", "new file");

    assertFalse(root.hasEntry("dir/file"));
    assertTrue(root.hasEntry("dir/new file"));

    assertEquals(c("content"), root.getEntry("dir/new file").getContent());
  }

  @Test
  public void testRenamingFilesUnderDirectoryToExistingNameThrowsException() {
    root.createDirectory(1, "dir");
    root.createFile(2, "dir/file1", null, -1);
    root.createFile(3, "dir/file2", null, -1);

    try {
      root.rename("dir/file1", "file2");
      fail();
    }
    catch (RuntimeException e) {
    }
  }

  @Test
  public void testRenamingSubdirectories() {
    root.createDirectory(1, "dir1");
    root.createFile(2, "dir1/dir2", null, -1);

    root.rename("dir1/dir2", "new dir");

    assertTrue(root.hasEntry("dir1/new dir"));
    assertFalse(root.hasEntry("dir1/dir2"));
  }

  @Test
  public void testRenamingDirectoryWithContent() {
    root.createDirectory(1, "dir");
    root.createFile(2, "dir/file", null, -1);

    root.rename("dir", "new dir");

    assertTrue(root.hasEntry("new dir"));
    assertTrue(root.hasEntry("new dir/file"));

    assertFalse(root.hasEntry("dir"));
    assertFalse(root.hasEntry("dir/file"));
  }

  @Test
  public void testRenamingDirectoryToExistingFileNameThrowsException() {
    root.createDirectory(1, "dir1");
    root.createDirectory(2, "dir1/dir2");
    root.createFile(3, "dir1/file", null, -1);

    try {
      root.rename("dir1/dir2", "file");
      fail();
    }
    catch (RuntimeException e) {
    }
  }

  @Test
  public void testMovingFilesBetweenDirectories() {
    root.createDirectory(1, "dir1");
    root.createDirectory(2, "dir2");
    root.createFile(3, "dir1/file", c("content"), -1);

    root.move("dir1/file", "dir2");

    assertTrue(root.hasEntry("dir2/file"));
    assertFalse(root.hasEntry("dir1/file"));

    Entry e = root.getEntry("dir2/file");
    assertEquals(c("content"), e.getContent());
  }

  @Test
  public void testMovingDirectories() {
    root.createDirectory(1, "root1");
    root.createDirectory(2, "root2");
    root.createDirectory(3, "root1/dir");
    root.createFile(4, "root1/dir/file", null, -1);

    root.move("root1/dir", "root2");

    assertTrue(root.hasEntry("root2/dir"));
    assertTrue(root.hasEntry("root2/dir/file"));

    assertFalse(root.hasEntry("root1/dir"));
  }

  @Test
  public void testDoesNotChangeTimestampOfContentWhenMoving() {
    root.createDirectory(1, "dir1");
    root.createDirectory(2, "dir2");
    root.createFile(3, "dir1/file", null, 99L);

    root.move("dir1", "dir2");

    assertEquals(99L, root.getEntry("dir2/dir1/file").getTimestamp());
  }

  @Test
  public void testMovingEntryFromRootToDirectory() {
    root.createDirectory(1, "dir");
    root.createFile(2, "file", null, -1);

    root.move("file", "dir");

    assertTrue(root.hasEntry("dir/file"));
    assertFalse(root.hasEntry("file"));
  }

  @Test
  public void testMovingEntriesToAnotherLevelInTree() {
    root.createDirectory(1, "dir1");
    root.createDirectory(2, "dir1/dir2");

    root.createFile(3, "dir1/file1", null, -1);
    root.createFile(4, "dir1/dir2/file2", null, -1);

    root.move("dir1/file1", "dir1/dir2");
    root.move("dir1/dir2/file2", "dir1");

    assertTrue(root.hasEntry("dir1/file2"));
    assertTrue(root.hasEntry("dir1/dir2/file1"));
  }

  @Test
  public void testMovingDirectoryToItsChildThrowsException() {
    root.createDirectory(1, "dir1");
    root.createDirectory(2, "dir1/dir2");

    try {
      root.move("dir1", "dir1/dir2");
      fail();
    }
    catch (RuntimeException e) {
    }
  }

  @Test
  public void testMovingEntryToNotADirectoryThrowsException() {
    root.createFile(1, "file1", null, -1);
    root.createFile(2, "file2", null, -1);

    try {
      root.move("file1", "file1/file2");
      fail();
    }
    catch (RuntimeException e) {
    }
  }

  @Test
  public void testMovingEntryToSameDirectory() {
    root.createDirectory(1, "dir");
    root.createFile(2, "dir/file", null, -1);

    root.move("dir/file", "dir");

    assertTrue(root.hasEntry("dir/file"));
  }

  @Test
  public void testDeletingDirectory() {
    root.createDirectory(1, "dir");
    assertTrue(root.hasEntry("dir"));

    root.delete("dir");
    assertFalse(root.hasEntry("dir"));
  }

  @Test
  public void testDeletingSubdirectory() {
    root.createDirectory(1, "dir1");
    root.createDirectory(2, "dir1/dir2");

    assertTrue(root.hasEntry("dir1"));
    assertTrue(root.hasEntry("dir1/dir2"));

    root.delete("dir1/dir2");
    assertFalse(root.hasEntry("dir1/dir2"));

    assertTrue(root.hasEntry("dir1"));
  }

  @Test
  public void testDeletingDirectoryWithContent() {
    root.createDirectory(1, "dir1");
    root.createDirectory(2, "dir1/dir2");

    root.delete("dir1");

    assertFalse(root.hasEntry("dir1/dir2"));
    assertFalse(root.hasEntry("dir1"));
  }

  @Test
  public void testDeletingFilesUnderDirectory() {
    root.createDirectory(1, "dir");
    root.createFile(2, "dir/file", null, -1);
    assertTrue(root.hasEntry("dir/file"));

    root.delete("dir/file");
    assertFalse(root.hasEntry("dir/file"));
  }
}
