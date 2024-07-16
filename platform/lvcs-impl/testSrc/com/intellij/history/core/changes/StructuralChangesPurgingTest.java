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

import com.intellij.history.core.Content;
import com.intellij.history.core.LocalHistoryTestCase;
import com.intellij.history.core.tree.RootEntry;
import org.junit.Test;

import java.util.List;

public class StructuralChangesPurgingTest extends LocalHistoryTestCase {
  private final RootEntry root = new RootEntry();

  @Test
  public void testChangeFileContentChange() {
    createFile(root, "f", "old");

    Change c = changeContent(root, "f", "new");

    List<Content> cc = c.getContentsToPurge();

    assertEquals(1, cc.size());
    assertContent("old", cc.get(0));
  }

  @Test
  public void testDeleteFileChange() {
    createFile(root, "f", "content");

    Change c = delete(root, "f");

    List<Content> cc = c.getContentsToPurge();

    assertEquals(1, cc.size());
    assertContent("content", cc.get(0));
  }

  @Test
  public void testDeleteDirectoryWithFilesChange() {
    createDirectory(root, "dir");
    createDirectory(root, "dir/subDir");
    createFile(root, "dir/file", "one");
    createFile(root, "dir/subDir/file1", "two");
    createFile(root, "dir/subDir/file2", "three");

    Change c = delete(root, "dir");

    List<Content> cc = c.getContentsToPurge();

    assertEquals(3, cc.size());
    assertTrue(cc.contains(c("one")));
    assertTrue(cc.contains(c("two")));
    assertTrue(cc.contains(c("three")));
  }

  @Test
  public void testOtherChanges() {
    Change c1 = createFile(root, "file", "content");
    Change c2 = createDirectory(root, "dir");
    Change c3 = move(root, "file", "dir");
    Change c4 = rename(root, "dir/file", "newFile");

    assertTrue(c1.getContentsToPurge().isEmpty());
    assertTrue(c2.getContentsToPurge().isEmpty());
    assertTrue(c3.getContentsToPurge().isEmpty());
    assertTrue(c4.getContentsToPurge().isEmpty());
  }
}
