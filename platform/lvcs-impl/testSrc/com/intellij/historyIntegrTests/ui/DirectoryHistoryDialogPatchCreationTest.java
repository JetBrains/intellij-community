package com.intellij.historyIntegrTests.ui;

import com.intellij.history.integration.ui.models.DirectoryHistoryDialogModel;
import com.intellij.historyIntegrTests.PatchingTestCase;

public class DirectoryHistoryDialogPatchCreationTest extends PatchingTestCase {
  public void testPatchCreation() throws Exception {
    root.createChildData(null, "f1.txt");
    root.createChildData(null, "f2.txt");
    root.createChildData(null, "f3.txt");

    DirectoryHistoryDialogModel m = new DirectoryHistoryDialogModel(gateway, getVcs(), root);
    m.selectRevisions(1, 3);
    m.createPatch(patchFilePath, false);
    clearRoot();

    applyPatch();

    assertNotNull(root.findChild("f1.txt"));
    assertNotNull(root.findChild("f2.txt"));
    assertNull(root.findChild("f3.txt"));
  }
}