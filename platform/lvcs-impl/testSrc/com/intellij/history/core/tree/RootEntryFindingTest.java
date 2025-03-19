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

package com.intellij.history.core.tree;

import com.intellij.history.core.LocalHistoryTestCase;
import com.intellij.history.core.Paths;
import org.junit.Test;

public class RootEntryFindingTest extends LocalHistoryTestCase {
  private RootEntry root = new RootEntry();

  @Test
  public void testFindingEntry() {
    FileEntry e = new FileEntry("file", null, -1, false);
    root.addChild(e);
    assertSame(e, root.findEntry("file"));
  }

  @Test
  public void testDoesNotFindUnknownEntry() {
    assertNull(root.findEntry("unknown entry"));
    assertNull(root.findEntry("root/unknown entry"));
  }

  @Test
  public void testFindingEntryUnderDirectory() {
    DirectoryEntry dir = new DirectoryEntry("dir");
    FileEntry file = new FileEntry("file", null, -1, false);

    dir.addChild(file);
    root.addChild(dir);

    assertSame(dir, root.findEntry("dir"));
    assertSame(file, root.findEntry("dir/file"));
    assertNull(root.findEntry("dir/another"));
  }

  @Test
  public void testFindingEntriesInTree() {
    root = new RootEntry();
    Entry dir = new DirectoryEntry("dir");
    Entry file1 = new FileEntry("file1", null, -1, false);
    Entry file2 = new FileEntry("file2", null, -1, false);

    root.addChild(dir);
    root.addChild(file1);
    dir.addChild(file2);

    assertSame(dir, root.findEntry("dir"));
    assertSame(file1, root.findEntry("file1"));
    assertSame(file2, root.findEntry("dir/file2"));
  }

  @Test
  public void testFindingUnderRoots() {
    Entry dir1 = new DirectoryEntry("c:");
    Entry dir2 = new DirectoryEntry("d:");
    Entry file1 = new FileEntry("file1", null, -1, false);
    Entry file2 = new FileEntry("file2", null, -1, false);
    dir1.addChild(file1);
    dir2.addChild(file2);
    root.addChild(dir1);
    root.addChild(dir2);

    assertSame(dir1, root.findEntry("c:"));
    assertSame(dir2, root.findEntry("d:"));
    assertSame(file1, root.findEntry("c:/file1"));
    assertSame(file2, root.findEntry("d:/file2"));
  }

  @Test
  public void testNamesOfDirectoriesBeginningWithTheSameString() {
    Entry d1 = new DirectoryEntry("dir");
    Entry d2 = new DirectoryEntry("dir2");
    Entry f = new FileEntry("file", null, -1, false);

    root.addChild(d1);
    root.addChild(d2);
    d2.addChild(f);

    assertSame(f, root.findEntry("dir2/file"));
    assertNull(root.findEntry("dir/file"));
  }

  @Test
  public void testNamesOfEntriesBeginningWithSameStringAndLongerOneIsTheFirst() {
    Entry f1 = new FileEntry("file1", null, -1, false);
    Entry f2 = new FileEntry("file", null, -1, false);

    root.addChild(f1);
    root.addChild(f2);

    assertSame(f1, root.findEntry("file1"));
    assertSame(f2, root.findEntry("file"));
  }

  @Test
  public void testFindingIsRelativeToFileSystemCaseSensivity() {
    root.addChild(new FileEntry("file", null, -1, false));

    Paths.setCaseSensitive(true);
    assertNull(root.findEntry("FiLe"));
    assertNotNull(root.findEntry("file"));

    Paths.setCaseSensitive(false);
    assertNotNull(root.findEntry("FiLe"));
    assertNotNull(root.findEntry("file"));
  }

  @Test
  public void testGettingUnknownEntryThrowsException() {
    try {
      root.getEntry("unknown entry");
      fail();
    }
    catch (RuntimeException e) {
      assertEquals("entry 'unknown entry' not found", e.getMessage());
    }
  }
}
