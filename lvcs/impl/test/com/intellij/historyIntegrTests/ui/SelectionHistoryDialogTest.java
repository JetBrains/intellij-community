package com.intellij.historyIntegrTests.ui;

import com.intellij.history.core.ContentFactory;
import com.intellij.history.integration.revertion.Reverter;
import com.intellij.history.integration.ui.models.FileDifferenceModel;
import com.intellij.history.integration.ui.models.NullRevisionsProgress;
import com.intellij.history.integration.ui.models.RevisionProcessingProgress;
import com.intellij.history.integration.ui.views.SelectionHistoryDialog;
import com.intellij.history.integration.ui.views.SelectionHistoryDialogModel;
import com.intellij.historyIntegrTests.IntegrationTestCase;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.FragmentContent;
import com.intellij.openapi.vfs.VirtualFile;
import static org.easymock.classextension.EasyMock.*;

import java.io.IOException;
import java.util.List;

public class SelectionHistoryDialogTest extends IntegrationTestCase {
  private VirtualFile f;
  private FileDifferenceModel dm;
  private SelectionHistoryDialogModel m;

  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();

    f = root.createChildData(null, "f.java");
    f.setBinaryContent("a\nb\nc\n".getBytes(), -1, 123);
    f.setBinaryContent("a\nbc\nd\n".getBytes(), -1, 456);
    f.setBinaryContent("a\nbcd\ne\n".getBytes(), -1, 789);
  }

  public void testDialogWorks() throws IOException {
    SelectionHistoryDialog d = new SelectionHistoryDialog(gateway, f, 0, 0);
    d.close(0);
  }
  
  public void testTitles() throws IOException {
    f.rename(null, "ff.java");
    f.setBinaryContent(new byte[0]);

    initModelOnSecondLineAndSelectRevisions(1, 2);

    assertEquals(f.getPath(), dm.getTitle());
    assertTrue(dm.getLeftTitle(new NullRevisionsProgress()), dm.getLeftTitle(new NullRevisionsProgress()).endsWith(" - f.java"));
    assertTrue(dm.getRightTitle(new NullRevisionsProgress()), dm.getRightTitle(new NullRevisionsProgress()).endsWith(" - ff.java"));
  }

  public void testCalculationProgress() {
    initModelOnSecondLineAndSelectRevisions(3, 3);

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

  public void testRecreatingCalculatorAfterShowChangesOnlyOptionIsChanged() throws IOException {
    f.setBinaryContent(new byte[ContentFactory.MAX_CONTENT_LENGTH + 1], -1, 1234);
    getVcs().putUserLabel("label");

    initModelOnSecondLineAndSelectRevisions(0, 0);

    m.showChangesOnly(false);
    m.selectRevisions(0, 0);
    assertEquals("", new String(dm.getLeftDiffContent((new NullRevisionsProgress())).getBytes())); // shouldn't raise exceptions

    m.showChangesOnly(true);
    m.selectRevisions(0, 0);
    assertEquals("", new String(dm.getRightDiffContent((new NullRevisionsProgress())).getBytes())); // shouldn't raise exceptions
  }

  public void testDiffContents() throws IOException {
    initModelOnSecondLineAndSelectRevisions(1, 2);

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

  public void testDiffContentAndTitlesWhenSomeContentIsUnavailable() throws IOException {
    f.setBinaryContent(new byte[ContentFactory.MAX_CONTENT_LENGTH + 1], -1, 1234);
    f.setBinaryContent("a\nb\nc\n".getBytes(), -1, 2345);
    f.setBinaryContent("a\nbc\nd\n".getBytes(), -1, 3456);

    initModelOnSecondLineAndSelectRevisions(1, 4);

    assertTrue(dm.getLeftTitle(new NullRevisionsProgress()),
               dm.getLeftTitle(new NullRevisionsProgress()).endsWith(" - f.java - File content is not available"));
    assertTrue(dm.getRightTitle(new NullRevisionsProgress()),
               dm.getRightTitle(new NullRevisionsProgress()).endsWith(" - f.java"));

    DiffContent left = dm.getLeftDiffContent(new NullRevisionsProgress());
    assertEquals("", new String(left.getBytes()));

    DiffContent right = dm.getRightDiffContent(new NullRevisionsProgress());
    assertEquals("b", new String(right.getBytes()));
  }

  public void testRevert() throws IOException {
    initModelOnSecondLineAndSelectRevisions(1, 1);
    Reverter r = m.createReverter();
    r.revert();

    assertEquals("a\nbc\ne\n", new String(f.contentsToByteArray()));
  }

  public void testWarningUserAboutRevertOfWholeFileOnChangeRevert() throws IOException {
    initModelAndSelect(false, 1, 1);
    Reverter r = m.createReverter();

    List<String> questions = r.askUserForProceeding();
    assertEquals(2, questions.size());

    assertTrue(questions.get(0), questions.get(0).startsWith("There are some changes that have been done after this one"));
    assertTrue(questions.get(1), questions.get(1).startsWith("The action could only be reverted for whole file"));
  }

  private void initModelOnSecondLineAndSelectRevisions(int first, int second) {
    initModelAndSelect(true, first, second);
  }

  private void initModelAndSelect(boolean selectRevisions, int first, int second) {
    m = new SelectionHistoryDialogModel(gateway, getVcs(), f, 1, 1);
    if (selectRevisions) {
      m.selectRevisions(first, second);
    } else {
      m.selectChanges(first,  second);
    }
    dm = m.getDifferenceModel();
  }
}
