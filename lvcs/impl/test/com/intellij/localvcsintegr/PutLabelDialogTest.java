package com.intellij.localvcsintegr;

import com.intellij.localvcs.integration.ui.views.PutLabelDialog;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;

public class PutLabelDialogTest extends IntegrationTestCase {
  PutLabelDialog d1;
  PutLabelDialog d2;
  VirtualFile f;

  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();
    f = root.createChildData(null, "f.java");
  }

  @Override
  protected void tearDown() throws Exception {
    if (d1 != null) d1.dispose();
    if (d2 != null) d2.dispose();
    super.tearDown();
  }

  public void testPutLabelDialogWorks() {
    d1 = new PutLabelDialog(gateway, null);
    d2 = new PutLabelDialog(gateway, root);
  }

  public void testPutGlobalLabel() throws IOException {
    d1 = new PutLabelDialog(gateway, null);
    d1.doOKAction();

    assertEquals(3, getVcsRevisionsFor(root).size());
    assertEquals(2, getVcsRevisionsFor(f).size());
  }

  public void testPutFileLabel() throws IOException {
    d1 = new PutLabelDialog(gateway, f);
    d1.doOKAction();

    assertEquals(3, getVcsRevisionsFor(root).size()); // whole project is selected by default
    assertEquals(2, getVcsRevisionsFor(f).size());
  }
}