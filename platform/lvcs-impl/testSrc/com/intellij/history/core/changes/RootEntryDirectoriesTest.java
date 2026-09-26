/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.history.core.changes;

import com.intellij.history.core.LocalHistoryTestCase;
import com.intellij.history.core.tree.DirectoryEntry;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.core.tree.RootEntry;
import org.junit.Ignore;
import org.junit.Test;

public class RootEntryDirectoriesTest extends LocalHistoryTestCase {
  private final RootEntry root = new RootEntry();

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
    createFile(root, "dir/file");

    assertTrue(root.hasEntry("dir"));
    assertTrue(root.hasEntry("dir/file"));

    Entry dir = root.getEntry("dir");
    Entry file = root.getEntry("dir/file");

    assertEquals(1, dir.getChildren().size());
    assertSame(file, dir.getChildren().get(0));
    assertSame(dir, file.getParent());
  }

  @Test
  public void testCreatingFilesUnderNonExistingDirectoryCreatesIt() {
    createFile(root, "dir/file");
    assertTrue(root.hasEntry("dir"));
    assertTrue(root.hasEntry("dir/file"));
  }

  @Test
  public void testChangingFileContentUnderDirectory() {
    createDirectory(root, "dir");
    createFile(root, "dir/file", "content");

    changeContent(root, "dir/file", "new content");

    assertContent("new content", root.getEntry("dir/file").getContent());
  }

  @Test
  public void testRenamingDirectories() {
    createFile(root, "dir");

    rename(root, "dir", "new dir");

    assertTrue(root.hasEntry("new dir"));
    assertFalse(root.hasEntry("dir"));
  }

  @Test
  public void testRenamingFilesUnderDirectory() {
    createDirectory(root, "dir");
    createFile(root, "dir/file", "content");

    rename(root, "dir/file", "new file");

    assertFalse(root.hasEntry("dir/file"));
    assertTrue(root.hasEntry("dir/new file"));

    assertContent("content", root.getEntry("dir/new file").getContent());
  }

  @Test
  @Ignore
  public void testRenamingFilesUnderDirectoryToExistingNameThrowsException() {
    createDirectory(root, "dir");
    createFile(root, "dir/file1");
    createFile(root, "dir/file2");

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
    createFile(root, "dir1/dir2");

    rename(root, "dir1/dir2", "new dir");

    assertTrue(root.hasEntry("dir1/new dir"));
    assertFalse(root.hasEntry("dir1/dir2"));
  }

  @Test
  public void testRenamingDirectoryWithContent() {
    createDirectory(root, "dir");
    createFile(root, "dir/file");

    rename(root, "dir", "new dir");

    assertTrue(root.hasEntry("new dir"));
    assertTrue(root.hasEntry("new dir/file"));

    assertFalse(root.hasEntry("dir"));
    assertFalse(root.hasEntry("dir/file"));
  }

  @Test
  @Ignore
  public void testRenamingDirectoryToExistingFileNameThrowsException() {
    createDirectory(root, "dir1");
    createDirectory(root, "dir1/dir2");
    createFile(root, "dir1/file");

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
    createFile(root, "dir1/file", "content");

    move(root, "dir1/file", "dir2");

    assertTrue(root.hasEntry("dir2/file"));
    assertFalse(root.hasEntry("dir1/file"));

    Entry e = root.getEntry("dir2/file");
    assertContent("content", e.getContent());
  }

  @Test
  public void testMovingDirectories() {
    createDirectory(root, "root1");
    createDirectory(root, "root2");
    createDirectory(root, "root1/dir");
    createFile(root, "root1/dir/file");

    move(root, "root1/dir", "root2");

    assertTrue(root.hasEntry("root2/dir"));
    assertTrue(root.hasEntry("root2/dir/file"));

    assertFalse(root.hasEntry("root1/dir"));
  }

  @Test
  public void testMovingEntryFromRootToDirectory() {
    createDirectory(root, "dir");
    createFile(root, "file");

    move(root, "file", "dir");

    assertTrue(root.hasEntry("dir/file"));
    assertFalse(root.hasEntry("file"));
  }

  @Test
  public void testMovingEntriesToAnotherLevelInTree() {
    createDirectory(root, "dir1");
    createDirectory(root, "dir1/dir2");

    createFile(root, "dir1/file1");
    createFile(root, "dir1/dir2/file2");

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
    createFile(root, "file1");
    createFile(root, "file2");

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
    createFile(root, "dir/file");

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
    createFile(root, "dir/file");
    assertTrue(root.hasEntry("dir/file"));

    delete(root, "dir/file");
    assertFalse(root.hasEntry("dir/file"));
  }
}
