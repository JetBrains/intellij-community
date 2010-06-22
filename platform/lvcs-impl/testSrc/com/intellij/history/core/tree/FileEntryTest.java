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
import com.intellij.history.core.revisions.Difference;
import com.intellij.history.core.storage.StoredContent;
import org.junit.Ignore;
import org.junit.Test;

import java.util.List;
import java.util.ArrayList;

public class FileEntryTest extends LocalHistoryTestCase {
  @Test
  @Ignore
  public void testHasUnavailableContent() {
    Entry e1 = new FileEntry(null, c("abc"), -1, false);
    Entry e2 = new FileEntry(null, new StoredContent(-1), -1, false);

    assertFalse(e1.hasUnavailableContent());
    assertTrue(e2.hasUnavailableContent());
  }

  @Test
  public void testCopying() {
    FileEntry file = new FileEntry("name", c("content"), 123L, true);

    Entry copy = file.copy();

    assertEquals("name", copy.getName());
    assertContent("content", copy.getContent());
    assertEquals(123L, copy.getTimestamp());
    assertTrue(copy.isReadOnly());
  }

  @Test
  public void testDoesNotCopyParent() {
    DirectoryEntry parent = new DirectoryEntry(null);
    FileEntry file = new FileEntry(null, null, -1, false);

    parent.addChild(file);

    Entry copy = file.copy();
    assertNull(copy.getParent());
  }

  @Test
  public void testRenaming() {
    Entry e = new FileEntry("name", null, -1, false);
    e.setName("new name");
    assertEquals("new name", e.getName());
  }

  @Test
  public void testOutdated() {
    Entry e = new FileEntry("name", null, 2L, false);

    assertTrue(e.isOutdated(1L));
    assertTrue(e.isOutdated(3L));

    assertFalse(e.isOutdated(2L));
  }

  @Test
  public void testNoDifference() {
    FileEntry e1 = new FileEntry("name", c("content"), -1, false);
    FileEntry e2 = new FileEntry("name", c("content"), -1, false);

    assertTrue(Entry.getDifferencesBetween(e1, e2).isEmpty());
  }

  @Test
  public void testDifferenceInName() {
    Entry e1 = new FileEntry("name", c("content"), -1, false);
    Entry e2 = new FileEntry("another name", c("content"), -1, false);

    List<Difference> dd = Entry.getDifferencesBetween(e1, e2);
    assertDifference(dd, e1, e2);
  }

  @Test
  public void testDifferenceInNameIsAlwaysCaseSensitive() {
    Entry e1 = new FileEntry("name", c(""), -1, false);
    Entry e2 = new FileEntry("NAME", c(""), -1, false);

    Paths.setCaseSensitive(false);
    assertEquals(1, Entry.getDifferencesBetween(e1, e2).size());

    Paths.setCaseSensitive(true);
    assertEquals(1, Entry.getDifferencesBetween(e1, e2).size());
  }

  @Test
  public void testDifferenceInContent() {
    FileEntry e1 = new FileEntry("name", c("content"), -1, false);
    FileEntry e2 = new FileEntry("name", c("another content"), -1, false);

    List<Difference> dd = Entry.getDifferencesBetween(e1, e2);
    assertDifference(dd, e1, e2);
  }
  
  @Test
  public void testDifferenceInROStatus() {
    FileEntry e1 = new FileEntry("name", c("content"), -1, true);
    FileEntry e2 = new FileEntry("name", c("content"), -1, false);

    List<Difference> dd = Entry.getDifferencesBetween(e1, e2);
    assertDifference(dd, e1, e2);
  }

  @Test
  public void testAsCreatedDifference() {
    FileEntry e = new FileEntry(null, null, -1, false);
    assertDifference(Entry.getDifferencesBetween(null, e), null, e);
  }

  @Test
  public void testAsDeletedDifference() {
    FileEntry e = new FileEntry(null, null, -1, false);
    assertDifference(Entry.getDifferencesBetween(e, null), e, null);
  }

  private void assertDifference(List<Difference> dd, Entry left, Entry right) {
    assertEquals(1, dd.size());
    Difference d = dd.get(0);

    assertTrue(d.isFile());
    assertSame(left, d.getLeft());
    assertSame(right, d.getRight());
  }
}
