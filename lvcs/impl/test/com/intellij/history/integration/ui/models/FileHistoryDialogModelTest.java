package com.intellij.history.integration.ui.models;

import com.intellij.history.core.InMemoryLocalVcs;
import com.intellij.history.core.LocalVcsTestCase;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.integration.TestIdeaGateway;
import com.intellij.history.integration.TestVirtualFile;
import com.intellij.mock.MockEditorFactory;
import com.intellij.openapi.editor.EditorFactory;
import static org.easymock.classextension.EasyMock.*;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

public class FileHistoryDialogModelTest extends LocalVcsTestCase {
  private InMemoryLocalVcs vcs = new InMemoryLocalVcs();
  private FileHistoryDialogModel m;

  @Test
  public void testRevisionsAfterPurgeContainsCurrentVersion() {
    setCurrentTimestamp(10);
    long timestamp = -1;
    vcs.createFile("f", cf(""), timestamp, false);
    vcs.purgeObsolete(0);

    initModelFor("f");

    setCurrentTimestamp(20);
    List<Revision> rr = m.getRevisions();
    setCurrentTimestamp(30);

    assertEquals(1, rr.size());
    assertEquals(20L, rr.get(0).getTimestamp());
  }

  @Test
  public void testCanNotShowDifferenceIfOneOfEntriesHasUnavailableContent() {
    long timestamp = -1;
    vcs.createFile("f", cf("abc"), timestamp, false);
    vcs.changeFileContent("f", bigContentFactory(), -1);
    vcs.changeFileContent("f", cf("def"), -1);

    initModelFor("f");

    m.selectRevisions(0, 1);
    assertFalse(m.canShowDifference(new NullRevisionProcessingProgress()));

    m.selectRevisions(0, 2);
    assertTrue(m.canShowDifference(new NullRevisionProcessingProgress()));

    m.selectRevisions(1, 2);
    assertFalse(m.canShowDifference(new NullRevisionProcessingProgress()));
  }

  @Test
  public void testCanShowDifferenceProgress() {
    long timestamp = -1;
    vcs.createFile("f", cf("abc"), timestamp, false);
    vcs.changeFileContent("f", cf(("def")), -1);

    initModelFor("f");
    RevisionProcessingProgress p = createMock(RevisionProcessingProgress.class);
    p.processingLeftRevision();
    p.processingRightRevision();
    replay(p);

    m.canShowDifference(p);
    verify(p);
  }

  @Test
  public void testDifferenceModelTitles() {
    vcs.createFile("old", cf(""), (long)123, false);
    vcs.rename("old", "new");
    vcs.rename("new", "current");

    initModelFor("current");
    m.selectRevisions(1, 2);

    FileDifferenceModel dm = m.getDifferenceModel();
    assertTrue(dm.getLeftTitle().endsWith(" - old"));
    assertTrue(dm.getRightTitle().endsWith(" - new"));
  }

  @Test
  public void testTitleForCurrentRevision() {
    vcs.createFile("f", cf("content"), (long)123, false);
    initModelFor("f");

    FileDifferenceModel dm = m.getDifferenceModel();
    assertTrue(dm.getLeftTitle().endsWith(" - f"));
    assertEquals("Current", dm.getRightTitle());
  }

  @Test
  public void testDifferenceModelContents() {
    long timestamp = -1;
    vcs.createFile("f", cf("old"), timestamp, false);
    vcs.changeFileContent("f", cf("new"), -1);
    vcs.changeFileContent("f", cf("current"), -1);

    initModelFor("f");
    m.selectRevisions(1, 2);

    assertDifferenceModelContents("old", "new");
  }

  private void assertDifferenceModelContents(String left, String right) {
    FileDifferenceModel dm = m.getDifferenceModel();
    TestIdeaGateway gw = new TestIdeaGateway();
    EditorFactory ef = new MockEditorFactory();

    try {
      RevisionProcessingProgress p = new NullRevisionProcessingProgress();
      assertEquals(left, new String(dm.getLeftDiffContent(gw, ef, p).getBytes()));
      assertEquals(right, new String(dm.getRightDiffContent(gw, ef, p).getBytes()));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void initModelFor(String path) {
    initModelFor(path, new String(vcs.getEntry(path).getContent().getBytes()));
  }

  private void initModelFor(String path, String content) {
    TestVirtualFile f = new TestVirtualFile(path, content, -1);
    m = new FileHistoryDialogModel(new TestIdeaGateway(), vcs, f);
  }
}
