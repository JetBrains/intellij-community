package com.intellij.localvcs.integration.ui;

import com.intellij.localvcs.Difference;
import com.intellij.localvcs.LocalVcs;
import com.intellij.localvcs.TestLocalVcs;
import com.intellij.localvcs.integration.TestVirtualFile;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

import java.util.List;

public class DirectoryHistoryDialogModelTest {
  private LocalVcs vcs = new TestLocalVcs();
  private DirectoryHistoryDialogModel m;

  @Test
  public void testNoDifference() {
    vcs.createDirectory("dir", null);
    vcs.apply();

    initModelFor("dir");

    List<String> labels = m.getLabels();
    assertEquals(1, labels.size());

    m.selectLabels(0, 0);
    Difference d = m.getDifference();

    assertEquals(Difference.Kind.NOT_MODIFIED, d.getKind());
    assertTrue(d.getChildren().isEmpty());
  }

  @Test
  public void testDifference() {
    vcs.createDirectory("dir", null);
    vcs.apply();

    vcs.createFile("dir/file", null, null);
    vcs.apply();

    initModelFor("dir");

    List<String> labels = m.getLabels();
    assertEquals(2, labels.size());

    m.selectLabels(0, 1);
    Difference d = m.getDifference();
    assertEquals(1, d.getChildren().size());

    d = d.getChildren().get(0);
    assertEquals(Difference.Kind.CREATED, d.getKind());
  }

  private void initModelFor(String path) {
    m = new DirectoryHistoryDialogModel(new TestVirtualFile(path, null), vcs);
  }
}
