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

import com.intellij.history.integration.ui.models.DirectoryHistoryDialogModel;
import com.intellij.history.integration.ui.models.FileDifferenceModel;
import com.intellij.history.integration.ui.models.HistoryDialogModel;
import com.intellij.history.integration.ui.models.NullRevisionsProgress;
import com.intellij.history.integration.ui.views.DirectoryChange;
import com.intellij.history.integration.ui.views.DirectoryHistoryDialog;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.DocumentContent;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;

public class DirectoryHistoryDialogTest extends LocalHistoryUITestCase {
  public void testDialogWorks() throws IOException {
    DirectoryHistoryDialog d = new DirectoryHistoryDialog(gateway, root);
    d.close(0);
  }

  public void testFileDifference() throws IOException {
    VirtualFile f = root.createChildData(null, "f.txt");
    f.setBinaryContent("old".getBytes());
    f.setBinaryContent("new".getBytes());
    f.setBinaryContent("current".getBytes());

    HistoryDialogModel m = createModelAndSelectRevisions(1, 2);
    DirectoryChange c = (DirectoryChange)m.getChanges().get(0);

    DiffContent left = c.getFileDifferenceModel().getLeftDiffContent(new NullRevisionsProgress());
    DiffContent right = c.getFileDifferenceModel().getRightDiffContent(new NullRevisionsProgress());
    
    assertEquals("old", new String(left.getBytes()));
    assertEquals("new", new String(right.getBytes()));

    m.selectRevisions(0, 1);

    c = (DirectoryChange)m.getChanges().get(0);
    right = c.getFileDifferenceModel().getRightDiffContent(new NullRevisionsProgress());
    assertEquals("current", new String(right.getBytes()));

    assertTrue(right instanceof DocumentContent);
  }

  @Test
  public void testFileDifferenceModelWhenOneOfTheEntryIsNull() throws IOException {
    root.createChildData(null, "dummy.txt");

    getVcs().beginChangeSet();
    VirtualFile f = root.createChildData(null, "f.txt");
    f.setBinaryContent("content".getBytes(), -1, 123);
    getVcs().endChangeSet(null);

    f.delete(null);

    HistoryDialogModel dm = createModelAndSelectRevisions(0, 1);
    FileDifferenceModel m = ((DirectoryChange)dm.getChanges().get(0)).getFileDifferenceModel();

    assertTrue(m.getTitle(), m.getTitle().endsWith("f.txt"));
    assertTrue(m.getLeftTitle(new NullRevisionsProgress()).endsWith("f.txt"));
    assertEquals("File does not exist", m.getRightTitle(new NullRevisionsProgress()));
    assertContents(m, "content", "");

    dm.selectRevisions(1, 2);
    m = ((DirectoryChange)dm.getChanges().get(0)).getFileDifferenceModel();

    assertTrue(m.getTitle(), m.getTitle().endsWith("f.txt"));
    assertEquals("File does not exist", m.getLeftTitle(new NullRevisionsProgress()));
    assertTrue(m.getRightTitle(new NullRevisionsProgress()).endsWith("f.txt"));
    assertContents(m, "", "content");
  }

  private void assertContents(FileDifferenceModel m, String expectedLeft, String expectedRight) throws IOException {
    assertEquals(expectedLeft, new String(m.getLeftDiffContent(new NullRevisionsProgress()).getBytes()));
    assertEquals(expectedRight, new String(m.getRightDiffContent(new NullRevisionsProgress()).getBytes()));
  }

  public void testRevertion() throws Exception {
    root.createChildData(null, "f.txt");

    HistoryDialogModel m = createModelAndSelectRevision(1);
    m.createReverter().revert();

    assertNull(root.findChild("f.txt"));
  }

  public void testSelectionRevertion() throws Exception {
    root.createChildData(null, "f1.txt");
    root.createChildData(null, "f2.txt");

    DirectoryHistoryDialogModel m = createModelAndSelectRevision(2);
    DirectoryChange c = (DirectoryChange)m.getChanges().get(0);
    m.createRevisionReverter(Collections.singletonList(c.getDifference())).revert();

    assertNull(root.findChild("f1.txt"));
    assertNotNull(root.findChild("f2.txt"));
  }

  public void testChangeRevertion() throws Exception {
    VirtualFile dir = root.createChildDirectory(null, "oldDir");
    VirtualFile f = dir.createChildData(null, "f.txt");
    dir.rename(null, "newDir");
    f.move(null, root);

    HistoryDialogModel m = new DirectoryHistoryDialogModel(gateway, getVcs(), dir);
    m.selectChanges(1, 1); // rename
    m.createReverter().revert();

    assertEquals("oldDir", dir.getName());
    assertEquals(dir, f.getParent());
  }

  private DirectoryHistoryDialogModel createModelAndSelectRevision(int rev) {
    return createModelAndSelectRevisions(rev, rev);
  }

  private DirectoryHistoryDialogModel createModelAndSelectRevisions(int first, int second) {
    DirectoryHistoryDialogModel m = new DirectoryHistoryDialogModel(gateway, getVcs(), root);
    m.selectRevisions(first, second);
    return m;
  }
}
