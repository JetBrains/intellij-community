package com.intellij.localvcsintegr.ui;

import com.intellij.history.integration.ui.views.PutLabelDialog;
import com.intellij.localvcsintegr.IntegrationTestCase;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;

public class PutLabelDialogTest extends IntegrationTestCase {
  PutLabelDialog d;
  VirtualFile f;

  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();
    f = root.createChildData(null, "f.java");
  }

  @Override
  protected void tearDown() throws Exception {
    if (d != null) d.close(0);
    super.tearDown();
  }

  public void testPutLabelDialogWorks() {
    PutLabelDialog d1 = null;
    PutLabelDialog d2 = null;
    try {
      d1 = new PutLabelDialog(gateway, null);
      d2 = new PutLabelDialog(gateway, root);
    }
    finally {
      if (d1 != null) d1.close(0);
      if (d2 != null) d2.close(0);
    }
  }

  public void testPutGlobalLabel() throws IOException {
    d = new PutLabelDialog(gateway, null);
    d.doOKAction();

    assertEquals(3, getVcsRevisionsFor(root).size());
    assertEquals(2, getVcsRevisionsFor(f).size());
  }

  public void testPutFileLabel() throws IOException {
    d = new PutLabelDialog(gateway, f);
    d.selectFileLabel();
    d.doOKAction();

    assertEquals(2, getVcsRevisionsFor(root).size());
    assertEquals(2, getVcsRevisionsFor(f).size());
  }

  public void testCanNotPutLabelOnNotAFile() throws IOException {
    d = new PutLabelDialog(gateway, null);
    assertFalse(d.canPutLabelOnSelectedFile());
  }

  public void testCanNotPutLabelOnUnversionedFile() throws IOException {
    f = root.createChildData(null, "f.xxx");

    d = new PutLabelDialog(gateway, f);
    assertFalse(d.canPutLabelOnSelectedFile());
  }
}