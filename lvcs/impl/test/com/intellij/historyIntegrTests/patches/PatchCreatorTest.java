package com.intellij.historyIntegrTests.patches;

import com.intellij.history.core.revisions.Revision;
import com.intellij.history.core.revisions.Difference;
import com.intellij.history.integration.patches.PatchCreator;
import com.intellij.history.integration.ui.models.DirectoryDifferenceModel;
import com.intellij.historyIntegrTests.PatchingTestCase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vcs.changes.Change;

import java.util.List;

public class PatchCreatorTest extends PatchingTestCase {
  public void testCreationPatch() throws Exception {
    root.createChildData(null, "f.java");

    createPatchBetweenRevisions(1, 0);
    clearRoot();

    applyPatch();
    assertNotNull(root.findChild("f.java"));
  }

  public void testPatchBetweenTwoOldRevisions() throws Exception {
    root.createChildData(null, "f1.java");
    root.createChildData(null, "f2.java");
    root.createChildData(null, "f3.java");

    createPatchBetweenRevisions(3, 1);
    clearRoot();
    applyPatch();

    assertNotNull(root.findChild("f1.java"));
    assertNotNull(root.findChild("f2.java"));
    assertNull(root.findChild("f3.java"));
  }

  public void testRename() throws Exception {
    VirtualFile f = root.createChildData(null, "f.java");
    f.setBinaryContent(new byte[]{1});

    f.rename(null, "ff.java");

    createPatchBetweenRevisions(1, 0);
    f.rename(null, "f.java");
    applyPatch();

    VirtualFile patched = root.findChild("ff.java");
    assertNull(root.findChild("f.java"));
    assertNotNull(patched);
    assertEquals(1, patched.contentsToByteArray()[0]);
  }

  public void testReversePatch() throws Exception {
    root.createChildData(null, "f.java");

    createPatchBetweenRevisions(1, 0, true);
    applyPatch();

    assertNull(root.findChild("f.java"));
  }

  private void createPatchBetweenRevisions(int left, int right) throws Exception {
    createPatchBetweenRevisions(left, right, false);
  }

  private void createPatchBetweenRevisions(int left, int right, boolean reverse) throws Exception {
    List<Revision> rr = getVcsRevisionsFor(root);
    Revision l = rr.get(left);
    Revision r = rr.get(right);
    Difference d = l.getDifferenceWith(r);
    List<Change> cc = new DirectoryDifferenceModel(d).getPlainChanges();
    PatchCreator.create(gateway, cc, patchFilePath, reverse);
  }
}
