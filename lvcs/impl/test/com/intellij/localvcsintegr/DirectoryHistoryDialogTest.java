package com.intellij.localvcsintegr;

import com.intellij.localvcs.integration.ui.models.DirectoryHistoryDialogModel;
import com.intellij.localvcs.integration.ui.views.DirectoryHistoryDialog;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;

public class DirectoryHistoryDialogTest extends IntegrationTestCase {
  public void testDialogWorks() throws IOException {
    DirectoryHistoryDialog d = new DirectoryHistoryDialog(gateway, root);
    d.close(0);
  }

  public void testRevertion() throws Exception {
    root.createChildData(null, "f.java");

    DirectoryHistoryDialogModel m = new DirectoryHistoryDialogModel(gateway, getVcs(), root);
    m.selectRevisions(1, 1);
    m.createReverter().revert();

    assertNull(root.findChild("f.java"));
  }

  public void testSelectionRevertion() throws Exception {
    root.createChildData(null, "f1.java");
    root.createChildData(null, "f2.java");

    DirectoryHistoryDialogModel m = new DirectoryHistoryDialogModel(gateway, getVcs(), root);
    m.selectRevisions(2, 2);
    m.createRevisionReverter(m.getRootDifferenceNodeModel().getChildren().get(0)).revert();

    assertNull(root.findChild("f1.java"));
    assertNotNull(root.findChild("f2.java"));
  }

  public void testChangeRevertion() throws Exception {
    VirtualFile dir = root.createChildDirectory(null, "oldDir");
    VirtualFile f = dir.createChildData(null, "f.java");
    dir.rename(null, "newDir");
    f.move(null, root);

    DirectoryHistoryDialogModel m = new DirectoryHistoryDialogModel(gateway, getVcs(), dir);
    m.selectChanges(1, 1); // rename
    m.createReverter().revert();

    assertEquals("oldDir", dir.getName());
    assertEquals(dir, f.getParent());
  }
}