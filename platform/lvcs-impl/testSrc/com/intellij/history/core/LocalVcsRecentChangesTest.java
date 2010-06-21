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

package com.intellij.history.core;

import com.intellij.history.core.revisions.RecentChange;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.core.tree.RootEntry;
import org.junit.Test;

import java.util.List;

public class LocalVcsRecentChangesTest extends LocalHistoryTestCase {
  LocalHistoryFacade vcs = new InMemoryLocalHistoryFacade();
  RootEntry root = new RootEntry();

  @Test
  public void testRecentChanges() {
    vcs.beginChangeSet();
    add(vcs, createFile(root, "f1"));
    vcs.endChangeSet("a");

    vcs.beginChangeSet();
    add(vcs, createFile(root, "f2"));
    vcs.endChangeSet("b");

    List<RecentChange> cc = vcs.getRecentChanges(root);
    assertRecentChanges(cc, "b", "a");

    RecentChange c0 = cc.get(0);
    RecentChange c1 = cc.get(1);

    assertNotNull(findEntryAfter(c0, "f1"));
    assertNotNull(findEntryAfter(c0, "f2"));
    assertNotNull(findEntryBefore(c0, "f1"));
    assertNull(findEntryBefore(c0, "f2"));

    assertNotNull(findEntryAfter(c1, "f1"));
    assertNull(findEntryAfter(c1, "f2"));
    assertNull(findEntryBefore(c1, "f1"));
    assertNull(findEntryBefore(c1, "f2"));
  }

  @Test
  public void testDoesNotIncludeUnnamedChanges() {
    vcs.beginChangeSet();
    add(vcs, createFile(root, "f1"));
    vcs.endChangeSet("a");

    add(vcs, createFile(root, "f2"));

    vcs.beginChangeSet();
    add(vcs, createFile(root, "f3"));
    vcs.endChangeSet("b");

    List<RecentChange> cc = vcs.getRecentChanges(root);
    assertRecentChanges(cc, "b", "a");

    RecentChange c0 = cc.get(0);
    RecentChange c1 = cc.get(1);

    assertNotNull(findEntryAfter(c0, "f1"));
    assertNotNull(findEntryAfter(c0, "f2"));
    assertNotNull(findEntryAfter(c0, "f3"));
    assertNotNull(findEntryBefore(c0, "f1"));
    assertNotNull(findEntryBefore(c0, "f2"));
    assertNull(findEntryBefore(c0, "f3"));

    assertNotNull(findEntryAfter(c1, "f1"));
    assertNull(findEntryAfter(c1, "f2"));
    assertNull(findEntryAfter(c1, "f3"));
    assertNull(findEntryBefore(c1, "f1"));
    assertNull(findEntryBefore(c1, "f2"));
    assertNull(findEntryBefore(c1, "f3"));
  }

  @Test
  public void testDoesNotIncludeLocalFileChanges() {
    vcs.beginChangeSet();
    add(vcs, createFile(root, "f1"));
    vcs.endChangeSet("a");

    vcs.beginChangeSet();
    add(vcs, createFile(root, "f2"));
    add(vcs, changeContent(root, "f1", null));
    vcs.endChangeSet("b");

    vcs.beginChangeSet();
    add(vcs, changeContent(root, "f2", null));
    vcs.endChangeSet("c");

    List<RecentChange> cc = vcs.getRecentChanges(root);
    assertEquals(2, cc.size());
    assertEquals("b", cc.get(0).getChangeName());
    assertEquals("a", cc.get(1).getChangeName());
  }

  @Test
  public void testIncludeChangeSetsWithFileContentChangesOnly() {
    vcs.beginChangeSet();
    add(vcs, createFile(root, "f1"));
    add(vcs, createFile(root, "f2"));
    vcs.endChangeSet("a");

    vcs.beginChangeSet();
    add(vcs, changeContent(root, "f1", null));
    add(vcs, changeContent(root, "f2", null));
    vcs.endChangeSet("b");

    List<RecentChange> cc = vcs.getRecentChanges(root);
    assertEquals(2, cc.size());
    assertEquals("b", cc.get(0).getChangeName());
    assertEquals("a", cc.get(1).getChangeName());
  }

  @Test
  public void testDoesNotIncludeLabels() {
    vcs.beginChangeSet();
    add(vcs, createFile(root, "f"));
    vcs.endChangeSet("change");
    vcs.putUserLabel("label", "project");

    List<RecentChange> cc = vcs.getRecentChanges(root);
    assertEquals(1, cc.size());
    assertEquals("change", cc.get(0).getChangeName());
  }

  @Test
  public void testIncludeOnlyLastValuable20Changes() {
    for (int i = 0; i < 40; i++) {
      vcs.beginChangeSet();
      add(vcs, createFile(root, "f" + i));
      vcs.endChangeSet(String.valueOf(i));

      vcs.beginChangeSet();
      add(vcs, changeContent(root, "f" + i, null));
      vcs.endChangeSet(i + "_");
    }

    List<RecentChange> cc = vcs.getRecentChanges(root);
    assertEquals(20, cc.size());

    for (int i = 0; i < 20; i++) {
      assertEquals(String.valueOf(39 - i), cc.get(i).getChangeName());
    }
  }

  private void assertRecentChanges(List<RecentChange> cc, String... names) {
    assertEquals(names.length, cc.size());
    for (int i = 0; i < names.length; i++) {
      assertEquals(names[i], cc.get(i).getChangeName());
    }
  }

  private Entry findEntryAfter(RecentChange c, String path) {
    return ((RootEntry)c.getRevisionAfter().findEntry()).findEntry(path);
  }

  private Entry findEntryBefore(RecentChange c, String path) {
    return ((RootEntry)c.getRevisionBefore().findEntry()).findEntry(path);
  }
}
