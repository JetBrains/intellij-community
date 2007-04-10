package com.intellij.localvcsintegr;

import com.intellij.localvcs.integration.IdeaGateway;
import com.intellij.localvcs.integration.ui.views.PutLabelDialog;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;

public class PutLabelDialogTest extends IntegrationTestCase {
  PutLabelDialog d1;
  PutLabelDialog d2;
  VirtualFile f;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    runWriteAction(new Runnable() {
      public void run() {
        try {
          f = root.createChildData(null, "f.java");
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    });
  }

  @Override
  protected void tearDown() throws Exception {
    if (d1 != null) d1.dispose();
    if (d2 != null) d2.dispose();
    super.tearDown();
  }

  public void testPutLabelDialogWorks() {
    d1 = new PutLabelDialog(new IdeaGateway(myProject), null);
    d2 = new PutLabelDialog(new IdeaGateway(myProject), root);
  }

  public void testPutGlobalLabel() throws IOException {
    d1 = new PutLabelDialog(new IdeaGateway(myProject), null);
    d1.doOKAction();

    assertEquals(3, getVcsRevisionsFor(root).size());
    assertEquals(2, getVcsRevisionsFor(f).size());
  }

  public void testPutFileLabel() throws IOException {
    d1 = new PutLabelDialog(new IdeaGateway(myProject), f);
    d1.doOKAction();

    assertEquals(3, getVcsRevisionsFor(root).size()); // whole project is selected by default
    assertEquals(2, getVcsRevisionsFor(f).size());
  }
}