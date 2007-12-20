package com.intellij.historyIntegrTests.ui;

import com.intellij.history.integration.ui.models.EntireFileHistoryDialogModel;
import com.intellij.history.integration.ui.models.FileHistoryDialogModel;
import com.intellij.history.integration.ui.models.NullRevisionsProgress;
import com.intellij.history.integration.ui.models.RevisionProcessingProgress;
import com.intellij.history.integration.ui.views.FileHistoryDialog;
import com.intellij.historyIntegrTests.IntegrationTestCase;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.DocumentContent;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.Date;

public class FileHistoryDialogTest extends IntegrationTestCase {
  public void testDialogWorks() throws IOException {
    VirtualFile file = root.createChildData(null, "f.java");

    FileHistoryDialog d = new FileHistoryDialog(gateway, file);
    d.close(0);
  }

  public void testTitles() throws IOException {
    long leftTime = new Date(2001, 01, 03, 12, 0).getTime();
    long rightTime = new Date(2002, 02, 04, 14, 0).getTime();

    VirtualFile f = root.createChildData(null, "old.java");
    f.setBinaryContent("old".getBytes(), -1, leftTime);

    f.rename(null, "new.java");
    f.setBinaryContent("new".getBytes(), -1, rightTime);

    f.setBinaryContent(new byte[0]); // to create current content to skip.

    FileHistoryDialogModel m = createFileModelAndSelectRevisions(f, 1, 3);

    assertEquals(f.getPath(), m.getDifferenceModel().getTitle());

    assertEquals("03.02.01 12:00 - old.java", m.getDifferenceModel().getLeftTitle());
    assertEquals("04.03.02 14:00 - new.java", m.getDifferenceModel().getRightTitle());
  }

  public void testContent() throws IOException {
    VirtualFile f = root.createChildData(null, "f.java");
    f.setBinaryContent("old".getBytes());
    f.setBinaryContent("new".getBytes());
    f.setBinaryContent("current".getBytes());

    FileHistoryDialogModel m = createFileModelAndSelectRevisions(f, 1, 2);

    assertDiffContents("old", "new", m);
  }

  public void testContentWhenOnlyOneRevisionSelected() throws IOException {
    VirtualFile f = root.createChildData(null, "f.java");
    f.setBinaryContent("old".getBytes());
    f.setBinaryContent("new".getBytes());

    FileHistoryDialogModel m = createFileModelAndSelectRevisions(f, 1, 1);

    assertDiffContents("old", "new", m);
  }

  public void testContentForCurrentRevision() throws IOException {
    VirtualFile f = root.createChildData(null, "f.java");
    f.setBinaryContent("old".getBytes());
    f.setBinaryContent("current".getBytes());

    FileHistoryDialogModel m = createFileModelAndSelectRevisions(f, 0, 1);

    assertDiffContents("old", "current", m);
    assertEquals(DocumentContent.class, getRightDiffContent(m).getClass());
  }

  public void testRevertion() throws Exception {
    VirtualFile dir = root.createChildDirectory(null, "oldDir");
    VirtualFile f = dir.createChildData(null, "old.java");
    f.rename(null, "new.java");
    dir.rename(null, "newDir");

    FileHistoryDialogModel m = createFileModelAndSelectRevisions(f, 2, 2);
    m.createReverter().revert();

    assertEquals("old.java", f.getName());
    assertEquals(f.getParent(), root.findChild("oldDir"));
    assertEquals("newDir", dir.getName());
  }

  public void testChangeRevertion() throws Exception {
    VirtualFile dir = root.createChildDirectory(null, "oldDir");
    VirtualFile f = dir.createChildData(null, "old.java");
    f.rename(null, "new.java");
    dir.rename(null, "newDir");

    FileHistoryDialogModel m = createFileModel(f);
    m.selectChanges(1, 1);
    m.createReverter().revert();

    assertEquals("old.java", f.getName());
    assertEquals("oldDir", dir.getName());
    assertNull(root.findChild("newDir"));
  }

  public void testRevertLabelChange() throws Exception {
    VirtualFile f = root.createChildDirectory(null, "f.java");
    getVcs().putUserLabel("abc");

    FileHistoryDialogModel m = createFileModel(f);
    m.selectChanges(0, 0);
    m.createReverter().revert();

    assertNotNull(root.findChild("f.java"));
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
    return new EntireFileHistoryDialogModel(gateway, getVcs(), f);
  }

  private FileHistoryDialogModel createFileModelAndSelectRevisions(VirtualFile f, int first, int second) {
    FileHistoryDialogModel m = createFileModel(f);
    m.selectRevisions(first, second);
    return m;
  }
}