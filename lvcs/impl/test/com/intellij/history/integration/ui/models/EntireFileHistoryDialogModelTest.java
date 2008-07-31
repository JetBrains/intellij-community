package com.intellij.history.integration.ui.models;

import com.intellij.history.core.InMemoryLocalVcs;
import com.intellij.history.core.LocalVcsTestCase;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.integration.TestIdeaGateway;
import com.intellij.history.integration.TestVirtualFile;
import org.junit.Test;

import java.util.List;

public class EntireFileHistoryDialogModelTest extends LocalVcsTestCase {
  private InMemoryLocalVcs vcs = new InMemoryLocalVcs();
  private FileHistoryDialogModel m;

  @Test
  public void testRevisionsAfterPurgeContainsCurrentVersion() {
    vcs.createFile("f", cf(""), 10, false);
    vcs.purgeObsoleteAndSave(0);

    initModelFor("f");

    List<Revision> rr = m.getRevisions();

    assertEquals(1, rr.size());
    assertEquals(10L, rr.get(0).getTimestamp());
  }

  @Test
  public void testDifferenceModelTitles() {
    vcs.createFile("old", cf(""), 123, false);
    vcs.rename("old", "new");
    vcs.rename("new", "current");

    initModelFor("current");
    m.selectRevisions(1, 2);

    FileDifferenceModel dm = m.getDifferenceModel();
    assertTrue(dm.getLeftTitle(new NullRevisionsProgress()).endsWith(" - old"));
    assertTrue(dm.getRightTitle(new NullRevisionsProgress()).endsWith(" - new"));
  }

  @Test
  public void testTitleForCurrentRevision() {
    vcs.createFile("f", cf("content"), 123, false);
    initModelFor("f");

    FileDifferenceModel dm = m.getDifferenceModel();
    assertTrue(dm.getLeftTitle(new NullRevisionsProgress()).endsWith(" - f"));
    assertEquals("Current", dm.getRightTitle(new NullRevisionsProgress()));
  }

  private void initModelFor(String path) {
    initModelFor(path, new String(vcs.getEntry(path).getContent().getBytes()));
  }

  private void initModelFor(String path, String content) {
    TestVirtualFile f = new TestVirtualFile(path, content, -1);
    m = new EntireFileHistoryDialogModel(new TestIdeaGateway(), vcs, f);
  }
}
