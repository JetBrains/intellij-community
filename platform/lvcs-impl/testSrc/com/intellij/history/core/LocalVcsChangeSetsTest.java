// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.history.core;

import com.intellij.history.core.changes.ChangeSet;
import org.junit.Test;

import java.util.List;

public class LocalVcsChangeSetsTest extends LocalHistoryTestCase {
  LocalHistoryFacade vcs = new InMemoryLocalHistoryFacade();

  @Test
  public void testTreatingSeveralChangesDuringChangeSetAsOne() {
    vcs.beginChangeSet();
    vcs.created("dir", true);
    vcs.created("dir/one", false);
    vcs.created("dir/two", false);
    vcs.endChangeSet(null);

    assertEquals(1, collectChanges(vcs, "dir", null, null).size());
  }

  @Test
  public void testTreatingSeveralChangesOutsideOfChangeSetAsSeparate() {
    vcs.created("dir", true);
    vcs.created("dir/one", false);
    vcs.created("dir/two", false);

    vcs.beginChangeSet();
    vcs.endChangeSet(null);

    vcs.created("dir/three", false);
    vcs.created("dir/four", false);

    assertEquals(5, collectChanges(vcs, "dir", null, null).size());
  }

  @Test
  public void testIgnoringInnerChangeSets() {
    vcs.beginChangeSet();
    vcs.created("dir", true);
    vcs.beginChangeSet();
    vcs.created("dir/one", false);
    vcs.endChangeSet("inner");
    vcs.created("dir/two", false);
    vcs.endChangeSet("outer");

    List<ChangeSet> changes = collectChanges(vcs, "dir", null, null);
    assertEquals(1, changes.size());
    assertEquals("outer", changes.get(0).getName());
  }

  @Test
  public void testIgnoringEmptyChangeSets() {
    vcs.beginChangeSet();
    vcs.created("dir", true);
    vcs.endChangeSet(null);

    assertEquals(1, vcs.getChangeListInTests().getChangesInTests().size());

    vcs.beginChangeSet();
    vcs.endChangeSet(null);

    assertEquals(1, vcs.getChangeListInTests().getChangesInTests().size());
  }
}