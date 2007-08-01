package com.intellij.historyIntegrTests.ui;

import com.intellij.history.integration.ui.models.DirectoryHistoryDialogModel;
import com.intellij.history.integration.ui.models.HistoryDialogModel;
import com.intellij.history.integration.ui.views.DirectoryHistoryDialog;
import com.intellij.historyIntegrTests.PatchingTestCase;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;

public class DirectoryHistoryDialogTest extends PatchingTestCase {
  public void testDialogWorks() throws IOException {
    DirectoryHistoryDialog d = new DirectoryHistoryDialog(gateway, root);
    d.close(0);
  }

  public void testRevertion() throws Exception {
    root.createChildData(null, "f.java");

    HistoryDialogModel m = createModelAndSelectRevision(1);
    m.createReverter().revert();

    assertNull(root.findChild("f.java"));
  }

  public void testSelectionRevertion() throws Exception {
    root.createChildData(null, "f1.java");
    root.createChildData(null, "f2.java");

    DirectoryHistoryDialogModel m = createModelAndSelectRevision(2);
    m.createRevisionReverter(m.getRootDifferenceModel().getChildren().get(0)).revert();

    assertNull(root.findChild("f1.java"));
    assertNotNull(root.findChild("f2.java"));
  }

  public void testChangeRevertion() throws Exception {
    VirtualFile dir = root.createChildDirectory(null, "oldDir");
    VirtualFile f = dir.createChildData(null, "f.java");
    dir.rename(null, "newDir");
    f.move(null, root);

    HistoryDialogModel m = new DirectoryHistoryDialogModel(gateway, getVcs(), dir);
    m.selectChanges(1, 1); // rename
    m.createReverter().revert();

    assertEquals("oldDir", dir.getName());
    assertEquals(dir, f.getParent());
  }

  public void testPatchCreation() throws Exception {
    root.createChildData(null, "f1.java");
    root.createChildData(null, "f2.java");
    root.createChildData(null, "f3.java");

    HistoryDialogModel m = createModelAndSelectRevision(1, 3);
    m.createPatch(patchFilePath, false);
    clearRoot();

    applyPatch();

    assertNotNull(root.findChild("f1.java"));
    assertNotNull(root.findChild("f2.java"));
    assertNull(root.findChild("f3.java"));
  }

  private DirectoryHistoryDialogModel createModelAndSelectRevision(int rev) {
    return createModelAndSelectRevision(rev, rev);
  }

  private DirectoryHistoryDialogModel createModelAndSelectRevision(int first, int second) {
    DirectoryHistoryDialogModel m = new DirectoryHistoryDialogModel(gateway, getVcs(), root);
    m.selectRevisions(first, second);
    return m;
  }
}