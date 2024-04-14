// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.history.integration.ui;

import com.intellij.history.core.changes.ChangeSet;
import com.intellij.history.core.revisions.Difference;
import com.intellij.history.core.tree.DirectoryEntry;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.integration.IntegrationTestCase;
import com.intellij.history.integration.ui.models.DirectoryChangeModel;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.List;

public class DirectoryChangeModelTest extends IntegrationTestCase {
  public void testNames() throws IOException {
    VirtualFile f = createDirectory("foo");
    rename(f, "bar");

    List<ChangeSet> revs = getChangesFor(f);

    Difference d = new Difference(false, getCurrentEntry(f), getEntryFor(revs.get(0), f));
    DirectoryChangeModel m = createModelOn(d);

    assertEquals("bar", m.getEntryName(0));
    assertEquals("foo", m.getEntryName(1));
  }

  public void testNamesForAbsentEntries() {
    Difference d = new Difference(false, null, null);
    DirectoryChangeModel m = createModelOn(d);

    assertEquals("", m.getEntryName(0));
    assertEquals("", m.getEntryName(1));
  }

  public void testCanShowFileDifference() throws IOException {
    VirtualFile f = createFile("foo.txt");
    setContent(f, "xxx");

    List<ChangeSet> revs = getChangesFor(f);

    Difference d1 = new Difference(true, getCurrentEntry(f), getEntryFor(revs.get(0), f));
    Difference d2 = new Difference(true, null, getEntryFor(revs.get(0), f));
    Difference d3 = new Difference(true, getEntryFor(revs.get(0), f), null);

    assertTrue(createModelOn(d1).canShowFileDifference());
    assertTrue(createModelOn(d2).canShowFileDifference());
    assertTrue(createModelOn(d3).canShowFileDifference());
  }

  public void testCanNotShowFileDifferenceForDirectories() {
    Entry left = new DirectoryEntry("left");
    Entry right = new DirectoryEntry("right");

    Difference d = new Difference(false, left, right);
    assertFalse(createModelOn(d).canShowFileDifference());
  }

  private DirectoryChangeModel createModelOn(Difference d) {
    return new DirectoryChangeModel(d, myGateway);
  }
}
