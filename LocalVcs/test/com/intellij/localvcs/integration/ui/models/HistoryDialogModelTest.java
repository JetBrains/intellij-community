package com.intellij.localvcs.integration.ui.models;

import com.intellij.localvcs.ILocalVcs;
import com.intellij.localvcs.LocalVcs;
import com.intellij.localvcs.LocalVcsTestCase;
import com.intellij.localvcs.TestLocalVcs;
import com.intellij.localvcs.integration.TestVirtualFile;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.Test;

public class HistoryDialogModelTest extends LocalVcsTestCase {
  LocalVcs vcs = new TestLocalVcs();
  HistoryDialogModel m;

  @Test
  public void testDoesNotRecomputeLabelsEachTime() {
    vcs.createFile("f", null, null);
    vcs.apply();

    initModelFor("f");

    assertEquals(1, m.getLabels().size());

    vcs.changeFileContent("f", b("bbb"), null);
    vcs.apply();

    assertEquals(1, m.getLabels().size());
  }

  private void initModelFor(String name) {
    VirtualFile f = new TestVirtualFile(name, null, null);
    m = new MyHistoryDialogModel(f, vcs);
  }

  private class MyHistoryDialogModel extends HistoryDialogModel {
    public MyHistoryDialogModel(VirtualFile f, ILocalVcs vcs) {
      super(f, vcs);
    }
  }
}
