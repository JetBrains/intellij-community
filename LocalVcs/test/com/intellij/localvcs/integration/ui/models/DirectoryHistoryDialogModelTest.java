package com.intellij.localvcs.integration.ui.models;

import static com.intellij.localvcs.Difference.Kind.CREATED;
import static com.intellij.localvcs.Difference.Kind.NOT_MODIFIED;
import com.intellij.localvcs.LocalVcs;
import com.intellij.localvcs.TestLocalVcs;
import com.intellij.localvcs.integration.TestVirtualFile;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class DirectoryHistoryDialogModelTest {
  private LocalVcs vcs = new TestLocalVcs();
  private DirectoryHistoryDialogModel m;

  @Test
  public void testTitle() {
    TestVirtualFile parent = new TestVirtualFile("parent", null, null);
    TestVirtualFile file = new TestVirtualFile("file", null, null);
    parent.addChild(file);

    m = new DirectoryHistoryDialogModel(file, vcs);

    assertEquals("parent/file", m.getTitle());
  }

  @Test
  public void testNoDifference() {
    vcs.createDirectory("dir", null);
    vcs.apply();

    initModelFor("dir");

    assertEquals(1, this.m.getLabels().size());

    this.m.selectLabels(0, 0);
    DirectoryDifferenceModel nm = this.m.getRootDifferenceNodeModel();

    assertEquals(NOT_MODIFIED, nm.getDifferenceKind());
    assertEquals(0, nm.getChildren().size());
  }

  @Test
  public void testDifference() {
    vcs.createDirectory("dir", null);
    vcs.apply();

    vcs.createFile("dir/file", null, null);
    vcs.apply();

    initModelFor("dir");

    assertEquals(2, m.getLabels().size());

    m.selectLabels(0, 1);
    DirectoryDifferenceModel nm = m.getRootDifferenceNodeModel();
    assertEquals(1, nm.getChildren().size());

    nm = nm.getChildren().get(0);
    assertEquals(CREATED, nm.getDifferenceKind());
  }

  private void initModelFor(String path) {
    m = new DirectoryHistoryDialogModel(new TestVirtualFile(path, null), vcs);
  }
}
