package com.intellij.localvcs.integration.ui.models;

import com.intellij.localvcs.*;
import com.intellij.localvcs.integration.TestVirtualFile;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class HistoryDialogModelTest extends LocalVcsTestCase {
  LocalVcs vcs = new TestLocalVcs();
  HistoryDialogModel m;

  @Before
  public void setUp() {
    vcs.beginChangeSet();
    vcs.createFile("f", null, -1);
    vcs.endChangeSet("1");

    vcs.beginChangeSet();
    vcs.changeFileContent("f", null, -1);
    vcs.endChangeSet("2");

    vcs.beginChangeSet();
    vcs.changeFileContent("f", null, -1);
    vcs.endChangeSet("3");

    initModelFor("f");
  }

  @Test
  public void testLabelsList() {
    List<Label> ll = m.getLabels();

    assertEquals(3, ll.size());
    assertEquals("3", ll.get(0).getName());
    assertEquals("2", ll.get(1).getName());
    assertEquals("1", ll.get(2).getName());
  }

  @Test
  public void testDoesNotRecomputeLabelsEachTime() {
    assertEquals(3, m.getLabels().size());

    vcs.changeFileContent("f", null, -1);

    assertEquals(3, m.getLabels().size());
  }

  @Test
  public void testSelectingLastLabelByDefault() {
    assertEquals("3", m.getLeftLabel().getName());
    assertEquals("3", m.getRightLabel().getName());
  }

  @Test
  public void testSelectingOnlyOneLabelSetsRightToLastOne() {
    m.selectLabels(0, 0);
    assertEquals("3", m.getLeftLabel().getName());
    assertEquals("3", m.getRightLabel().getName());

    m.selectLabels(1, 1);
    assertEquals("2", m.getLeftLabel().getName());
    assertEquals("3", m.getRightLabel().getName());
  }

  @Test
  public void testSelectingTwoLabels() {
    m.selectLabels(0, 1);
    assertEquals("2", m.getLeftLabel().getName());
    assertEquals("3", m.getRightLabel().getName());

    m.selectLabels(1, 2);
    assertEquals("1", m.getLeftLabel().getName());
    assertEquals("2", m.getRightLabel().getName());
  }

  @Test
  public void testClearingSelectionSetsLabelsToLastOnes() {
    m.selectLabels(-1, -1);
    assertEquals("3", m.getLeftLabel().getName());
    assertEquals("3", m.getRightLabel().getName());
  }

  private void initModelFor(String name) {
    VirtualFile f = new TestVirtualFile(name, null, -1);
    m = new MyHistoryDialogModel(f, vcs);
  }

  private class MyHistoryDialogModel extends HistoryDialogModel {
    public MyHistoryDialogModel(VirtualFile f, ILocalVcs vcs) {
      super(f, vcs, null);
    }
  }
}
