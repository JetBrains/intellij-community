package com.intellij.history.integration.ui.models;

import com.intellij.history.core.ILocalVcs;
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
  ILocalVcs vcs = new InMemoryLocalVcs();
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
