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

package com.intellij.history.integration.ui.models;

import com.intellij.history.core.LocalVcs;
import com.intellij.history.core.InMemoryLocalVcs;
import com.intellij.history.core.LocalVcsTestCase;
import com.intellij.history.core.revisions.RecentChange;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.TestIdeaGateway;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class RecentChangeDialogModelTest extends LocalVcsTestCase {
  LocalVcs vcs = new InMemoryLocalVcs();
  IdeaGateway gw = new TestIdeaGateway();
  RecentChange c;
  RecentChangeDialogModel m;

  @Before
  public void setUp() {
    vcs.beginChangeSet();
    vcs.createFile("f", null, -1, false);
    vcs.endChangeSet("change");

    c = vcs.getRecentChanges().get(0);
    m = new RecentChangeDialogModel(gw, vcs, c);
  }

  @Test
  public void testRevisions() {
    List<Revision> rr = m.getRevisions();
    assertEquals(2, rr.size());

    assertEquals(c.getRevisionBefore(), m.getLeftRevision());
    assertEquals(c.getRevisionAfter(), m.getRightRevision());

    assertNull(m.getLeftEntry().findEntry("f"));
    assertNotNull(m.getRightEntry().findEntry("f"));
  }

  @Test
  public void testRevisionsAfterChangingShowChangesOnlyOption() {
    m.showChangesOnly(true);

    assertEquals(c.getRevisionBefore(), m.getLeftRevision());
    assertEquals(c.getRevisionAfter(), m.getRightRevision());
  }

  @Test
  public void testTitle() {
    assertEquals("change", m.getTitle());
  }
}
