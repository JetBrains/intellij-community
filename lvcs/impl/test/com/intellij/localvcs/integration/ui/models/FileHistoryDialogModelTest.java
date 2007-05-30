package com.intellij.localvcs.integration.ui.models;

import com.intellij.localvcs.core.LocalVcsTestCase;
import com.intellij.localvcs.core.TestLocalVcs;
import com.intellij.localvcs.core.revisions.Revision;
import com.intellij.localvcs.integration.TestIdeaGateway;
import com.intellij.localvcs.integration.TestVirtualFile;
import com.intellij.mock.MockEditorFactory;
import com.intellij.openapi.editor.EditorFactory;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

public class FileHistoryDialogModelTest extends LocalVcsTestCase {
  private TestLocalVcs vcs = new TestLocalVcs();
  private FileHistoryDialogModel m;

  @Test
  public void testRevisionsAfterPurgeContainsCurrentVersion() {
    setCurrentTimestamp(10);
    vcs.createFile("f", cf(""), -1);
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
    vcs.createFile("f", cf("abc"), -1);
    vcs.changeFileContent("f", bigContentFactory(), -1);
    vcs.changeFileContent("f", cf("def"), -1);

    initModelFor("f");

    m.selectRevisions(0, 1);
    assertFalse(m.canShowDifference());

    m.selectRevisions(0, 2);
    assertTrue(m.canShowDifference());

    m.selectRevisions(1, 2);
    assertFalse(m.canShowDifference());
  }

  @Test
  public void testDifferenceModelTitles() {
    vcs.createFile("old", cf(""), 123);
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
    vcs.createFile("f", cf("content"), 123);
    initModelFor("f");

    FileDifferenceModel dm = m.getDifferenceModel();
    assertTrue(dm.getLeftTitle().endsWith(" - f"));
    assertEquals("Current", dm.getRightTitle());
  }

  @Test
  public void testDifferenceModelContents() {
    vcs.createFile("f", cf("old"), -1);
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
      assertEquals(left, new String(dm.getLeftDiffContent(gw, ef).getBytes()));
      assertEquals(right, new String(dm.getRightDiffContent(gw, ef).getBytes()));
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
