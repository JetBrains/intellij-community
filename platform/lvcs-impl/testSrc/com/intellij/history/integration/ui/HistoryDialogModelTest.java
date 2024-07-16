// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.history.integration.ui;

import com.intellij.history.core.changes.ChangeSet;
import com.intellij.history.core.tree.RootEntry;
import com.intellij.history.integration.IntegrationTestCase;
import com.intellij.history.integration.revertion.Reverter;
import com.intellij.history.integration.ui.models.HistoryDialogModel;
import com.intellij.history.integration.ui.models.RevisionItem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.lvcs.impl.statistics.LocalHistoryCounter;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class HistoryDialogModelTest extends IntegrationTestCase {
  HistoryDialogModel m;
  VirtualFile f;

  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();

    getVcs().beginChangeSet();
    f = createFile("f.txt", "1");
    getVcs().endChangeSet("1");

    getVcs().beginChangeSet();
    setContent(f, "2.txt");
    getVcs().endChangeSet("2");

    getVcs().beginChangeSet();
    setContent(f, "3.txt");
    getVcs().endChangeSet("3");

    initModelFor();
  }

  public void testRevisionsList() {
    List<RevisionItem> rr = m.getRevisions();

    assertEquals(3, rr.size());
    assertEquals("3", rr.get(0).revision.getChangeSetName());
    assertEquals("2", rr.get(1).revision.getChangeSetName());
    assertEquals("1", rr.get(2).revision.getChangeSetName());
  }

  public void testDoesNotRecomputeRevisionsEveryTime() {
    assertEquals(3, m.getRevisions().size());

    setContent(f, "xxx");
    assertEquals(3, m.getRevisions().size());
  }

  public void testRegisteringUnsavedDocumentsBeforeBuildingRevisionsList() {
    setDocumentTextFor(f, "unsaved");
    initModelFor();
    
    m.getRevisions();

    List<ChangeSet> rr = getChangesFor(f);
    assertEquals(4, rr.size());
    assertContent("unsaved", getCurrentEntry(f));
  }

  public void testSelectingLastRevisionByDefault() {
    String leftChangeName = "3";
    String rightChangeName = "3";
    assertSelectedRevisins(leftChangeName, rightChangeName);
  }

  public void testSelectingOnlyOneRevisionSetsRightToLastOne() {
    m.selectRevisions(0, 0);
    assertSelectedRevisins("3", null);

    m.selectRevisions(1, 1);
    assertSelectedRevisins("2", null);
  }

  public void testSelectingTwoRevisions() {
    m.selectRevisions(0, 1);
    assertSelectedRevisins("2", "3");

    m.selectRevisions(1, 2);
    assertSelectedRevisins("1", "2");
  }

  public void testClearingSelectionSetsRevisionsToLastOnes() {
    m.selectRevisions(-1, -1);
    assertSelectedRevisins("3", null);
  }

  public void testIsCurrentRevisionSelected() {
    m.selectRevisions(1, 2);
    assertFalse(m.isCurrentRevisionSelected());

    m.selectRevisions(2, 2);
    assertTrue(m.isCurrentRevisionSelected());

    m.selectRevisions(-1, -1);
    assertTrue(m.isCurrentRevisionSelected());
  }

  public void testIsRevertEnabledForRevision() {
    m.selectRevisions(1, 1);
    assertTrue(m.isRevertEnabled());

    m.selectRevisions(1, 2);
    assertTrue(m.isRevertEnabled());

    m.selectRevisions(2, 2);
    assertTrue(m.isRevertEnabled());

    m.selectRevisions(0, 1);
    assertTrue(m.isRevertEnabled());

    m.selectRevisions(0, 0);
    assertTrue(m.isRevertEnabled());

    m.selectRevisions(-1, -1);
    assertTrue(m.isRevertEnabled());
  }

  public void testIsCreatePatchEnabledForRevision() {
    m.selectRevisions(1, 1);
    assertTrue(m.isCreatePatchEnabled());

    m.selectRevisions(2, 2);
    assertTrue(m.isCreatePatchEnabled());

    m.selectRevisions(1, 2);
    assertTrue(m.isCreatePatchEnabled());

    m.selectRevisions(0, 1);
    assertTrue(m.isRevertEnabled());

    m.selectRevisions(0, 0);
    assertTrue(m.isCreatePatchEnabled());

    m.selectRevisions(-1, -1);
    assertTrue(m.isCreatePatchEnabled());
  }

  private void initModelFor() {
    m = new HistoryDialogModel(myProject, myGateway, getVcs(), f) {
      @Override
      protected @NotNull RootEntry createRootEntry() {
        return getRootEntry();
      }

      @Override
      public Reverter createReverter() {
        throw new UnsupportedOperationException();
      }

      @Override
      public @NotNull LocalHistoryCounter.Kind getKind() {
        throw new UnsupportedOperationException();
      }
    };
  }

  private void assertSelectedRevisins(String leftChangeName, String rightChangeName) {
    assertEquals(leftChangeName, m.getLeftRevision().getChangeSetName());
    assertEquals(rightChangeName, m.getRightRevision().getChangeSetName());
  }
}
