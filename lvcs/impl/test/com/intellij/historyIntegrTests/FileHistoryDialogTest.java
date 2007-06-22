package com.intellij.historyIntegrTests;

import com.intellij.history.integration.ui.models.FileHistoryDialogModel;
import com.intellij.history.integration.ui.models.NullRevisionProcessingProgress;
import com.intellij.history.integration.ui.models.RevisionProcessingProgress;
import com.intellij.history.integration.ui.views.FileHistoryDialog;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.DocumentContent;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;

public class FileHistoryDialogTest extends IntegrationTestCase {
  public void testDialogWorks() throws IOException {
    VirtualFile file = root.createChildData(null, "f.java");

    FileHistoryDialog d = new FileHistoryDialog(gateway, file);
    d.close(0);
  }

  public void testContentWhenOnlyOneRevisionSelected() throws IOException {
    VirtualFile f = root.createChildData(null, "f.java");
    f.setBinaryContent("old".getBytes());
    f.setBinaryContent("new".getBytes());

    FileHistoryDialogModel m = createFileModelFor(f);
    m.selectRevisions(1, 1);

    assertDiffContents("old", "new", m);
  }

  public void testContentForCurrentRevision() throws IOException {
    VirtualFile f = root.createChildData(null, "f.java");
    f.setBinaryContent("old".getBytes());
    f.setBinaryContent("current".getBytes());

    FileHistoryDialogModel m = createFileModelFor(f);
    m.selectRevisions(0, 1);

    assertDiffContents("old", "current", m);
    assertEquals(DocumentContent.class, getRightDiffContent(m).getClass());
  }

  public void testRevertion() throws Exception {
    VirtualFile dir = root.createChildDirectory(null, "oldDir");
    VirtualFile f = dir.createChildData(null, "old.java");
    f.rename(null, "new.java");
    dir.rename(null, "newDir");

    FileHistoryDialogModel m = createFileModelFor(f);
    m.selectRevisions(2, 2);
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

    FileHistoryDialogModel m = createFileModelFor(f);
    m.selectChanges(1, 1);
    m.createReverter().revert();

    assertEquals("old.java", f.getName());
    assertEquals("oldDir", dir.getName());
    assertNull(root.findChild("newDir"));
  }

  public void testRevertLabelChange() throws Exception {
    VirtualFile f = root.createChildDirectory(null, "f.java");
    getVcs().putLabel("abc");

    FileHistoryDialogModel m = createFileModelFor(f);
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
    EditorFactory ef = EditorFactory.getInstance();
    RevisionProcessingProgress p = new NullRevisionProcessingProgress();
    return m.getDifferenceModel().getLeftDiffContent(gateway, ef, p);
  }

  private DiffContent getRightDiffContent(FileHistoryDialogModel m) {
    EditorFactory ef = EditorFactory.getInstance();
    RevisionProcessingProgress p = new NullRevisionProcessingProgress();
    return m.getDifferenceModel().getRightDiffContent(gateway, ef, p);
  }

  private FileHistoryDialogModel createFileModelFor(VirtualFile f) {
    return new FileHistoryDialogModel(gateway, getVcs(), f);
  }
}