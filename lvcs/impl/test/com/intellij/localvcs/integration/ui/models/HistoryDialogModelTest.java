package com.intellij.localvcs.integration.ui.models;

import com.intellij.localvcs.core.InMemoryLocalVcs;
import com.intellij.localvcs.core.LocalVcs;
import com.intellij.localvcs.core.LocalVcsTestCase;
import com.intellij.localvcs.core.revisions.Revision;
import com.intellij.localvcs.integration.TestIdeaGateway;
import com.intellij.localvcs.integration.TestVirtualFile;
import com.intellij.localvcs.integration.revertion.RevisionReverter;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class HistoryDialogModelTest extends LocalVcsTestCase {
  LocalVcs vcs = new InMemoryLocalVcs();
  TestIdeaGateway gw = new TestIdeaGateway();
  HistoryDialogModel m;

  @Before
  public void setUp() {
    vcs.beginChangeSet();
    vcs.createFile("f", null, -1);
    vcs.endChangeSet("1");

    vcs.beginChangeSet();
    vcs.changeFileContent("f", null, -1);
    vcs.endChangeSet("2");

    vcs.beginChangeSet();
    vcs.changeFileContent("f", null, -1);
    vcs.endChangeSet("3");

    initModelFor("f");
  }

  @Test
  public void testRevisionsList() {
    List<Revision> rr = m.getRevisions();

    assertEquals(3, rr.size());
    assertEquals("3", rr.get(0).getCauseChangeName());
    assertEquals("2", rr.get(1).getCauseChangeName());
    assertEquals("1", rr.get(2).getCauseChangeName());
  }

  @Test
  public void testDoesNotRecomputeRevisionsEachTime() {
    assertEquals(3, m.getRevisions().size());

    vcs.changeFileContent("f", null, -1);
    assertEquals(3, m.getRevisions().size());
  }

  @Test
  public void testDisplayingOnlyChanges() {
    vcs.putLabel("label");

    m.showChangesOnly(false);
    List<Revision> rr = m.getRevisions();

    assertEquals(4, rr.size());
    assertEquals("label", rr.get(0).getName());

    m.showChangesOnly(true);
    rr = m.getRevisions();

    assertEquals(3, rr.size());
    assertEquals("3", rr.get(0).getCauseChangeName());
  }

  @Test
  public void testResettingSelectionOnShowChangesOnlyOptionChange() {
    m.selectChanges(1, 1);
    assertEquals("2", m.getRightRevision().getCauseChangeName());
    assertEquals("1", m.getLeftRevision().getCauseChangeName());

    m.showChangesOnly(false);
    assertEquals("3", m.getRightRevision().getCauseChangeName());
    assertEquals("3", m.getLeftRevision().getCauseChangeName());
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
    String leftChangeName = "3";
    String rightChangeName = "3";
    assertSelectedRevisins(leftChangeName, rightChangeName);
  }

  @Test
  public void testSelectingOnlyOneRevisionSetsRightToLastOne() {
    m.selectRevisions(0, 0);
    assertSelectedRevisins("3", "3");

    m.selectRevisions(1, 1);
    assertSelectedRevisins("2", "3");
  }

  @Test
  public void testSelectingTwoRevisions() {
    m.selectRevisions(0, 1);
    assertSelectedRevisins("2", "3");

    m.selectRevisions(1, 2);
    assertSelectedRevisins("1", "2");
  }

  @Test
  public void testClearingSelectionSetsRevisionsToLastOnes() {
    m.selectRevisions(-1, -1);
    assertSelectedRevisins("3", "3");
  }

  @Test
  public void testIsCurrentRevisionSelected() {
    m.selectRevisions(1, 2);
    assertFalse(m.isCurrentRevisionSelected());

    m.selectRevisions(2, 2);
    assertTrue(m.isCurrentRevisionSelected());

    m.selectRevisions(-1, -1);
    assertTrue(m.isCurrentRevisionSelected());
  }

  @Test
  public void testSelectingSingleChange() {
    m.selectChanges(0, 0);
    assertSelectedRevisins("2", "3");

    m.selectChanges(1, 1);
    assertSelectedRevisins("1", "2");
  }

  @Test
  public void testSelectingSeveralChanges() {
    vcs.beginChangeSet();
    vcs.changeFileContent("f", null, -1);
    vcs.endChangeSet("4");

    vcs.beginChangeSet();
    vcs.changeFileContent("f", null, -1);
    vcs.endChangeSet("5");

    initModelFor("f");

    m.selectChanges(0, 1);
    assertSelectedRevisins("3", "5");

    m.selectChanges(1, 2);
    assertSelectedRevisins("2", "4");

    m.selectChanges(0, 3);
    assertSelectedRevisins("1", "5");
  }

  @Test
  public void testIsRevertEnabledForRevision() {
    m.selectRevisions(1, 1);
    assertTrue(m.isRevertEnabled());

    m.selectRevisions(2, 2);
    assertTrue(m.isRevertEnabled());

    m.selectRevisions(0, 0);
    assertFalse(m.isRevertEnabled());

    m.selectRevisions(-1, -1);
    assertFalse(m.isRevertEnabled());

    m.selectRevisions(1, 2);
    assertFalse(m.isRevertEnabled());
  }

  @Test
  public void testIsRevertEnabledForChange() {
    m.selectChanges(0, 0);
    assertTrue(m.isRevertEnabled());

    m.selectChanges(1, 1);
    assertTrue(m.isRevertEnabled());

    m.selectChanges(0, 1);
    assertFalse(m.isRevertEnabled());
  }

  private void initModelFor(String name) {
    VirtualFile f = new TestVirtualFile(name, null, -1);
    m = new HistoryDialogModel(gw, vcs, f) {
      @Override
      protected RevisionReverter createRevisionReverter() {
        throw new UnsupportedOperationException();
      }
    };
  }

  private void assertSelectedRevisins(String leftChangeName, String rightChangeName) {
    assertEquals(leftChangeName, m.getLeftRevision().getCauseChangeName());
    assertEquals(rightChangeName, m.getRightRevision().getCauseChangeName());
  }
}
