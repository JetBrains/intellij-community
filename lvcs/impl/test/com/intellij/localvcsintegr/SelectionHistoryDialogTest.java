package com.intellij.localvcsintegr;

import com.intellij.localvcs.integration.ui.views.SelectionHistoryDialog;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;

public class SelectionHistoryDialogTest extends IntegrationTestCase {
  public void testDialogWorks() throws IOException {
    VirtualFile f = root.createChildData(null, "f.java");
    f.setBinaryContent("abc".getBytes());

    SelectionHistoryDialog d = new SelectionHistoryDialog(gateway, f, 0, 1);
    d.close(0);
  }
}
