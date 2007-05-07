package com.intellij.localvcs.integration.ui.models;

import com.intellij.localvcs.core.LocalVcs;
import com.intellij.localvcs.core.LocalVcsTestCase;
import com.intellij.localvcs.core.TestLocalVcs;
import com.intellij.localvcs.core.revisions.Revision;
import com.intellij.localvcs.integration.TestIdeaGateway;
import com.intellij.localvcs.integration.TestVirtualFile;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class HistoryDialogModelTest extends LocalVcsTestCase {
  LocalVcs vcs = new TestLocalVcs();
  TestIdeaGateway gw = new TestIdeaGateway();
  HistoryDialogModel m;

  @Before
  public void setUp() {
    vcs.beginChangeSet();
    vcs.createFile("f", cf(""), -1);
    vcs.endChangeSet("1");

    vcs.beginChangeSet();
    vcs.changeFileContent("f", cf(""), -1);
    vcs.endChangeSet("2");

    vcs.beginChangeSet();
    vcs.changeFileContent("f", cf(""), -1);
    vcs.endChangeSet("3");

    initModelFor("f");
  }

  @Test
  public void testRevisionsList() {
    List<Revision> rr = m.getRevisions();

    assertEquals(3, rr.size());
    assertEquals("3", rr.get(0).getCauseAction());
    assertEquals("2", rr.get(1).getCauseAction());
    assertEquals("1", rr.get(2).getCauseAction());
  }

  @Test
  public void testDoesNotRecomputeRevisionsEachTime() {
    assertEquals(3, m.getRevisions().size());

    vcs.changeFileContent("f", null, -1);
    assertEquals(3, m.getRevisions().size());
  }

  @Test
  public void testRegisteringUnsavedDocumentsBeforeBuildingRevisionsList() {
    gw.addUnsavedDocument("f", "unsaved");
    initModelFor("f");

    List<Revision> rr = m.getRevisions();
    assertEquals(4, rr.size());
    assertEquals(c("unsaved"), rr.get(0).getEntry().getContent());

    rr = vcs.getRevisionsFor("f");
    assertEquals(4, rr.size());
    assertEquals(c("unsaved"), rr.get(0).getEntry().getContent());
  }

  @Test
  public void testSelectingLastRevisionByDefault() {
    assertEquals("3", m.getLeftRevision().getCauseAction());
    assertEquals("3", m.getRightRevision().getCauseAction());
  }

  @Test
  public void testSelectingOnlyOneRevisionSetsRightToLastOne() {
    m.selectRevisions(0, 0);
    assertEquals("3", m.getLeftRevision().getCauseAction());
    assertEquals("3", m.getRightRevision().getCauseAction());

    m.selectRevisions(1, 1);
    assertEquals("2", m.getLeftRevision().getCauseAction());
    assertEquals("3", m.getRightRevision().getCauseAction());
  }

  @Test
  public void testSelectingTwoRevisions() {
    m.selectRevisions(0, 1);
    assertEquals("2", m.getLeftRevision().getCauseAction());
    assertEquals("3", m.getRightRevision().getCauseAction());

    m.selectRevisions(1, 2);
    assertEquals("1", m.getLeftRevision().getCauseAction());
    assertEquals("2", m.getRightRevision().getCauseAction());
  }

  @Test
  public void testClearingSelectionSetsRevisionsToLastOnes() {
    m.selectRevisions(-1, -1);
    assertEquals("3", m.getLeftRevision().getCauseAction());
    assertEquals("3", m.getRightRevision().getCauseAction());
  }

  @Test
  public void testCanRevert() {
    m.selectRevisions(1, 1);
    assertTrue(m.canRevert());

    m.selectRevisions(2, 2);
    assertTrue(m.canRevert());

    m.selectRevisions(0, 0);
    assertFalse(m.canRevert());

    m.selectRevisions(-1, -1);
    assertFalse(m.canRevert());

    m.selectRevisions(1, 2);
    assertFalse(m.canRevert());
  }

  @Test
  public void testCantRevertIfRevisionHasUnavailableContent() {
    vcs.changeFileContent("f", bigContentFactory(), -1);
    vcs.changeFileContent("f", cf("current"), -1);

    m.selectRevisions(1, 1);
    assertFalse(m.canRevert());
  }

  private void initModelFor(String name) {
    VirtualFile f = new TestVirtualFile(name, null, -1);
    m = new HistoryDialogModel(f, vcs, gw) {
    };
  }
}
