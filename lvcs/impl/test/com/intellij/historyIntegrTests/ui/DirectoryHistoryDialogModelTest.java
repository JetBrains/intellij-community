package com.intellij.historyIntegrTests.ui;

import com.intellij.history.core.InMemoryLocalVcs;
import com.intellij.history.core.LocalVcs;
import com.intellij.history.integration.TestIdeaGateway;
import com.intellij.history.integration.TestVirtualFile;
import com.intellij.history.integration.ui.models.DirectoryHistoryDialogModel;
import com.intellij.history.integration.ui.views.DirectoryChange;
import com.intellij.historyIntegrTests.IntegrationTestCase;
import com.intellij.openapi.vcs.changes.Change;

import java.util.List;

// todo should be integration test case because ...vcs.Change creation requires idea environment 
public class DirectoryHistoryDialogModelTest extends IntegrationTestCase {
  private LocalVcs vcs = new InMemoryLocalVcs();
  private DirectoryHistoryDialogModel m;

  public void testTitle() {
    TestVirtualFile parent = new TestVirtualFile("parent", null, -1);
    TestVirtualFile file = new TestVirtualFile("file", null, -1);
    parent.addChild(file);

    m = new DirectoryHistoryDialogModel(null, vcs, file);

    assertEquals("parent/file", m.getTitle());
  }

  public void testNoDifference() {
    vcs.createDirectory("dir");
    initModelFor("dir");

    assertEquals(1, m.getRevisions().size());

    m.selectRevisions(0, 0);
    assertTrue(m.getChanges().isEmpty());
  }

  public void testDifference() {
    vcs.createDirectory("dir");
    vcs.createFile("dir/file", null, -1);

    initModelFor("dir");

    assertEquals(2, m.getRevisions().size());

    m.selectRevisions(0, 1);
    List<Change> cc = m.getChanges();
    assertEquals(1, cc.size());
    assertEquals("file", ((DirectoryChange)cc.get(0)).getModel().getEntryName(1));
  }

  private void initModelFor(String path) {
    m = new DirectoryHistoryDialogModel(new TestIdeaGateway(), vcs, new TestVirtualFile(path));
  }
}
