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
import org.junit.Test;

public class ChangeSetTest extends LocalHistoryTestCase {
  @Test
  public void testIsCreational() {
    ChangeSet cs1 = cs(new CreateFileChange(nextId(), "file"));
    ChangeSet cs2 = cs(new CreateDirectoryChange(nextId(), "dir"));

    assertTrue(isCreationalFor(cs1, "file"));
    assertTrue(isCreationalFor(cs2, "dir"));

    assertFalse(isCreationalFor(cs1, "dir"));
  }

  @Test
  public void testNonCreational() {
    ChangeSet cs = cs(new ContentChange(nextId(), "file", null, -1));
    assertFalse(isCreationalFor(cs, "file"));
  }

  @Test
  public void testCreationalAndNonCreationalInOneChangeSet() {
    ChangeSet cs = cs(new CreateFileChange(nextId(), "file"), new ContentChange(nextId(), "file", null, -1));
    assertTrue(isCreationalFor(cs, "file"));
  }

  @Test
  public void testToCreationalChangesInOneChangeSet() {
    ChangeSet cs = cs(new CreateFileChange(nextId(), "file"), new CreateDirectoryChange(nextId(), "dir"));
    assertTrue(isCreationalFor(cs, "file"));
    assertTrue(isCreationalFor(cs, "dir"));
  }

  @Test
  public void testIsFileContentChange() {
    assertFalse(cs(new CreateFileChange(nextId(), "f")).isContentChangeOnly());
    assertTrue(cs(new ContentChange(nextId(), "f", null, -1)).isContentChangeOnly());
    assertFalse(cs(new ContentChange(nextId(), "f1", null, -1), new ContentChange(nextId(), "f2", null, -1)).isContentChangeOnly());
    assertFalse(cs(new CreateFileChange(nextId(), "f1"), new ContentChange(nextId(), "f2", null, -1)).isContentChangeOnly());
  }

  private static boolean isCreationalFor(ChangeSet changeSet, String path) {
    return changeSet.anyChangeMatches(change -> change.isCreationalFor(path));
  }
}
