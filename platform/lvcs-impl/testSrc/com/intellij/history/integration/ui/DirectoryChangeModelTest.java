// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.history.integration.ui;

import com.intellij.history.core.changes.ChangeSet;
import com.intellij.history.core.revisions.Difference;
import com.intellij.history.core.tree.DirectoryEntry;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.integration.IntegrationTestCase;
import com.intellij.history.integration.ui.views.DirectoryChange;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.List;

public class DirectoryChangeModelTest extends IntegrationTestCase {
  public void testNames() {
    VirtualFile f = createDirectory("foo");
    rename(f, "bar");

    List<ChangeSet> revs = getChangesFor(f);

    Difference d = new Difference(false, getCurrentEntry(f), getEntryFor(revs.get(0), f));
    DirectoryChange change = new DirectoryChange(myGateway, d);

    assertEquals("bar", change.getLeftEntry().getName());
    assertEquals("foo", change.getRightEntry().getName());
  }

  public void testCanShowFileDifference() throws IOException {
    VirtualFile f = createFile("foo.txt");
    setContent(f, "xxx");

    List<ChangeSet> revs = getChangesFor(f);

    Difference d1 = new Difference(true, getCurrentEntry(f), getEntryFor(revs.get(0), f));
    Difference d2 = new Difference(true, null, getEntryFor(revs.get(0), f));
    Difference d3 = new Difference(true, getEntryFor(revs.get(0), f), null);

    assertTrue(new DirectoryChange(myGateway, d1).canShowFileDifference());
    assertTrue(new DirectoryChange(myGateway, d2).canShowFileDifference());
    assertTrue(new DirectoryChange(myGateway, d3).canShowFileDifference());
  }

  public void testCanNotShowFileDifferenceForDirectories() {
    Entry left = new DirectoryEntry("left");
    Entry right = new DirectoryEntry("right");

    Difference d = new Difference(false, left, right);
    assertFalse(new DirectoryChange(myGateway, d).canShowFileDifference());
  }
}
