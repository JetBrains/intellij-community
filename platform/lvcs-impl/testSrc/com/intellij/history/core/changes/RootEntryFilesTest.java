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

import com.intellij.history.core.LocalVcsTestCase;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.core.tree.FileEntry;
import com.intellij.history.core.tree.RootEntry;
import org.junit.Test;

public class RootEntryFilesTest extends LocalVcsTestCase {
  private final Entry root = new RootEntry();

  @Test
  public void testCreatingFiles() {
    assertFalse(root.hasEntry("file"));

    createFile(root, -1, "file", c("content"), 42);
    assertTrue(root.hasEntry("file"));

    Entry e = root.getEntry("file");

    assertEquals(FileEntry.class, e.getClass());
    assertEquals(c("content"), e.getContent());
    assertEquals(42L, e.getTimestamp());
  }

  @Test
  public void testCreatingTwoFiles() {
    createFile(root, -1, "file1", null, -1);
    createFile(root, -1, "file2", null, -1);

    assertTrue(root.hasEntry("file1"));
    assertTrue(root.hasEntry("file2"));

    assertFalse(root.hasEntry("unknown file"));
  }

  @Test
  public void testChangingFileContent() {
    createFile(root, 1, "file", c("content"), -1);
    changeFileContent(root, "file", c("new content"), 77);

    Entry e = root.getEntry("file");

    assertEquals(c("new content"), e.getContent());
    assertEquals(77L, e.getTimestamp());
  }

  @Test
  public void testChangingFileContentAffectsOnlyOneFile() {
    createFile(root, 1, "file1", c("content1"), -1);
    createFile(root, 2, "file2", c("content2"), -1);

    changeFileContent(root, "file1", c("new content"), -1);

    assertEquals(c("new content"), root.getEntry("file1").getContent());
    assertEquals(c("content2"), root.getEntry("file2").getContent());
  }

  @Test
  public void testChangingContentOfAnNonExistingFileThrowsException() {
    try {
      changeFileContent(root, "unknown file", c("content"), -1);
      fail();
    }
    catch (RuntimeException e) {
    }
  }

  @Test
  public void testRenamingFiles() {
    createFile(root, 1, "file", c("content"), -1);

    rename(root, "file", "new file");

    assertFalse(root.hasEntry("file"));

    Entry e = root.findEntry("new file");

    assertNotNull(e);
    assertEquals(c("content"), e.getContent());
  }

  @Test
  public void testRenamingOfAnNonExistingFileThrowsException() {
    try {
      rename(root, "unknown file", "new file");
      fail();
    }
    catch (RuntimeException e) {
    }
  }

  @Test
  public void testRenamingFileToAnExistingFileNameThrowsException() {
    createFile(root, 1, "file1", null, -1);
    createFile(root, 2, "file2", null, -1);

    try {
      rename(root, "file1", "file2");
      fail();
    }
    catch (RuntimeException e) {
    }
  }

  @Test
  public void testRenamingFileToSameNameDoesNothing() {
    createFile(root, 1, "file", c("content"), -1);

    rename(root, "file", "file");

    assertTrue(root.hasEntry("file"));
    assertEquals(c("content"), root.getEntry("file").getContent());
  }

  @Test
  public void testDeletingFiles() {
    createFile(root, 1, "file", null, -1);

    delete(root, "file");

    assertFalse(root.hasEntry("file"));
  }

  @Test
  public void testDeletingOnlyOneFile() {
    createFile(root, 1, "file1", null, -1);
    createFile(root, 2, "file2", null, -1);

    delete(root, "file2");

    assertTrue(root.hasEntry("file1"));
    assertFalse(root.hasEntry("file2"));
  }

  @Test
  public void testDeletionOfAnUnknownEntryThrowsException() {
    try {
      delete(root, "unknown file");
      fail();
    }
    catch (RuntimeException e) {
    }
  }
}
