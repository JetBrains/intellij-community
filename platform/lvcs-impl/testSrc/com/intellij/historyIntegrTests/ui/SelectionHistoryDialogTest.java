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

import com.intellij.history.integration.revertion.Reverter;
import com.intellij.history.integration.ui.models.FileDifferenceModel;
import com.intellij.history.integration.ui.models.NullRevisionsProgress;
import com.intellij.history.integration.ui.models.RevisionProcessingProgress;
import com.intellij.history.integration.ui.views.SelectionHistoryDialog;
import com.intellij.history.integration.ui.views.SelectionHistoryDialogModel;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.FragmentContent;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;

import static org.easymock.classextension.EasyMock.*;

public class SelectionHistoryDialogTest extends LocalHistoryUITestCase {
  private VirtualFile f;
  private FileDifferenceModel dm;
  private SelectionHistoryDialogModel m;

  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();

    f = myRoot.createChildData(null, "f.txt");
    f.setBinaryContent("a\nb\nc\n".getBytes(), -1, 123);
    f.setBinaryContent("a\nbc\nd\n".getBytes(), -1, 456);
    f.setBinaryContent("a\nbcd\ne\n".getBytes(), -1, 789);
  }

  public void testDialogWorks() throws IOException {
    SelectionHistoryDialog d = new SelectionHistoryDialog(myProject, myGateway, f, 0, 0);
    Disposer.dispose(d);
  }

  public void testTitles() throws IOException {
    f.rename(null, "ff.txt");
    f.setBinaryContent(new byte[0]);

    initModelOnSecondLineAndSelectRevisions(0, 1);

    assertEquals(FileUtil.toSystemDependentName(f.getPath()), dm.getTitle());
    assertTrue(dm.getLeftTitle(new NullRevisionsProgress()), dm.getLeftTitle(new NullRevisionsProgress()).endsWith(" - f.txt"));
    assertTrue(dm.getRightTitle(new NullRevisionsProgress()), dm.getRightTitle(new NullRevisionsProgress()).endsWith(" - ff.txt"));
  }

  public void testCalculationProgress() {
    initModelOnSecondLineAndSelectRevisions(2, 2);

    RevisionProcessingProgress p = createMock(RevisionProcessingProgress.class);
    p.processed(25);
    p.processed(50);
    p.processed(75);
    p.processed(100);
    replay(p);

    dm.getLeftTitle(p);
    verify(p);

    reset(p);
    // already processed - shouldn't process one more time
    replay(p);

    dm.getRightTitle(p);
    verify(p);
  }

  public void testDiffContents() throws IOException {
    initModelOnSecondLineAndSelectRevisions(0, 1);

    DiffContent left = dm.getLeftDiffContent(new NullRevisionsProgress());
    DiffContent right = dm.getRightDiffContent(new NullRevisionsProgress());

    assertEquals("b", new String(left.getBytes()));
    assertEquals("bc", new String(right.getBytes()));
  }

  public void testDiffContentsAndTitleForCurrentRevision() throws IOException {
    initModelOnSecondLineAndSelectRevisions(0, 0);

    assertEquals("Current", dm.getRightTitle(new NullRevisionsProgress()));

    DiffContent right = dm.getRightDiffContent(new NullRevisionsProgress());

    assertEquals("bcd", new String(right.getBytes()));
    assertTrue(right instanceof FragmentContent);
  }

  public void testDiffForDeletedAndRecreatedFile() throws Exception {
    byte[] bytes = f.contentsToByteArray();
    f.delete(this);

    f = createFile(f.getName(), new String(bytes));

    initModelOnSecondLineAndSelectRevisions(3, 3);

    assertEquals("b", new String(dm.getLeftDiffContent(new NullRevisionsProgress()).getBytes()));
    assertEquals("bcd", new String(dm.getRightDiffContent(new NullRevisionsProgress()).getBytes()));
  }

  public void testRevert() throws IOException {
    initModelOnSecondLineAndSelectRevisions(0, 0);
    Reverter r = m.createReverter();
    r.revert();

    assertEquals("a\nbc\ne\n", new String(f.contentsToByteArray()));
  }

  private void initModelOnSecondLineAndSelectRevisions(int first, int second) {
    m = new SelectionHistoryDialogModel(myProject, myGateway, getVcs(), f, 1, 1);
    m.selectRevisions(first, second);
    dm = m.getDifferenceModel();
  }
}
