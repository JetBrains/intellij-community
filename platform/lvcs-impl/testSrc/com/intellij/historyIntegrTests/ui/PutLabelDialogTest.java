/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.historyIntegrTests.ui;

import com.intellij.history.integration.ui.views.PutLabelDialog;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;

public class PutLabelDialogTest extends LocalHistoryUITestCase {
  PutLabelDialog d;
  VirtualFile f;

  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();
    f = root.createChildData(null, "f.txt");
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

  public void testRegisterUnsavedDocumentsContentBeforeLabeling() throws Exception {
    f.setBinaryContent(new byte[] {1});

    setDocumentTextFor(f, new byte[] {2});
    d = new PutLabelDialog(gateway, f);
    d.doOKAction();

    assertEquals(2, getVcsContentOf(f)[0]);
  }
}