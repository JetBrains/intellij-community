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

import com.intellij.history.core.revisions.Revision;
import com.intellij.history.core.tree.RootEntry;
import org.jetbrains.annotations.Nullable;
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

    assertEquals(2, collectRevisions(vcs, null, "dir", null, null).size());
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

    assertEquals(6, collectRevisions(vcs, null, "dir", null, null).size());
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

    List<Revision> rr = collectRevisions(vcs, null, "dir", null, null);
    assertEquals(2, rr.size());
    assertEquals("outer", rr.get(1).getChangeSetName());
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