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

package com.intellij.history.integration.ui;

import com.intellij.history.integration.ui.models.EntireFileHistoryDialogModel;
import com.intellij.history.integration.ui.models.FileHistoryDialogModel;
import com.intellij.history.integration.ui.models.NullRevisionsProgress;
import com.intellij.history.integration.ui.models.RevisionProcessingProgress;
import com.intellij.history.integration.ui.views.FileHistoryDialog;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.DocumentContent;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.text.DateFormatUtil;

import java.io.IOException;
import java.util.Date;

public class FileHistoryDialogTest extends LocalHistoryUITestCase {
  public void testDialogWorks() throws IOException {
    VirtualFile file = myRoot.createChildData(null, "f.txt");

    FileHistoryDialog d = new FileHistoryDialog(myProject, myGateway, file);
    Disposer.dispose(d);
  }

  public void testTitles() throws IOException {
    long leftTime = new Date(2001 - 1900, 1, 3, 12, 0).getTime();
    long rightTime = new Date(2002 - 1900, 2, 4, 14, 0).getTime();

    VirtualFile f = myRoot.createChildData(null, "old.txt");
    f.setBinaryContent("old".getBytes(), -1, leftTime);

    f.rename(null, "new.txt");
    f.setBinaryContent("new".getBytes(), -1, rightTime);

    f.setBinaryContent(new byte[0]); // to create current content to skip.

    FileHistoryDialogModel m = createFileModelAndSelectRevisions(f, 0, 2);
    assertEquals(FileUtil.toSystemDependentName(f.getPath()), m.getDifferenceModel().getTitle());

    assertEquals(DateFormatUtil.formatPrettyDateTime(leftTime) + " - old.txt",
                 m.getDifferenceModel().getLeftTitle(new NullRevisionsProgress()));
    assertEquals(DateFormatUtil.formatPrettyDateTime(rightTime) + " - new.txt",
                 m.getDifferenceModel().getRightTitle(new NullRevisionsProgress()));
  }

  public void testContent() throws IOException {
    VirtualFile f = myRoot.createChildData(null, "f.txt");
    f.setBinaryContent("old".getBytes());
    f.setBinaryContent("new".getBytes());
    f.setBinaryContent("current".getBytes());

    FileHistoryDialogModel m = createFileModelAndSelectRevisions(f, 0, 1);

    assertDiffContents("old", "new", m);
  }

  public void testContentWhenOnlyOneRevisionSelected() throws IOException {
    VirtualFile f = myRoot.createChildData(null, "f.txt");
    f.setBinaryContent("old".getBytes());
    f.setBinaryContent("new".getBytes());

    FileHistoryDialogModel m = createFileModelAndSelectRevisions(f, 0, 0);

    assertDiffContents("old", "new", m);
  }

  public void testContentForCurrentRevision() throws IOException {
    VirtualFile f = myRoot.createChildData(null, "f.txt");
    f.setBinaryContent("old".getBytes());
    f.setBinaryContent("current".getBytes());

    FileHistoryDialogModel m = createFileModelAndSelectRevisions(f, 0, 0);

    assertDiffContents("old", "current", m);
    assertEquals(DocumentContent.class, getRightDiffContent(m).getClass());
  }

  public void testRevertion() throws Exception {
    VirtualFile dir = myRoot.createChildDirectory(null, "oldDir");
    VirtualFile f = dir.createChildData(null, "old.txt");
    f.rename(null, "new.txt");
    dir.rename(null, "newDir");

    FileHistoryDialogModel m = createFileModelAndSelectRevisions(f, 1, 1);
    m.createReverter().revert();

    assertEquals("old.txt", f.getName());
    assertEquals(f.getParent(), dir);
    assertEquals("newDir", dir.getName());
  }

  private void assertDiffContents(String leftContent, String rightContent, FileHistoryDialogModel m) throws IOException {
    DiffContent left = getLeftDiffContent(m);
    DiffContent right = getRightDiffContent(m);

    assertEquals(leftContent, new String(left.getBytes()));
    assertEquals(rightContent, new String(right.getBytes()));
  }

  private DiffContent getLeftDiffContent(FileHistoryDialogModel m) {
    RevisionProcessingProgress p = new NullRevisionsProgress();
    return m.getDifferenceModel().getLeftDiffContent(p);
  }

  private DiffContent getRightDiffContent(FileHistoryDialogModel m) {
    RevisionProcessingProgress p = new NullRevisionsProgress();
    return m.getDifferenceModel().getRightDiffContent(p);
  }

  private FileHistoryDialogModel createFileModel(VirtualFile f) {
    return new EntireFileHistoryDialogModel(myProject, myGateway, getVcs(), f);
  }

  private FileHistoryDialogModel createFileModelAndSelectRevisions(VirtualFile f, int first, int second) {
    FileHistoryDialogModel m = createFileModel(f);
    m.selectRevisions(first, second);
    return m;
  }
}
