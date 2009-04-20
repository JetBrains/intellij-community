package com.intellij.historyIntegrTests.patches;

import com.intellij.history.core.revisions.Difference;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.integration.patches.PatchCreator;
import com.intellij.historyIntegrTests.PatchingTestCase;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.ArrayList;
import java.util.List;

public class PatchCreatorTest extends PatchingTestCase {
  public void testCreationPatch() throws Exception {
    root.createChildData(null, "f.txt");

    createPatchBetweenRevisions(1, 0);
    clearRoot();

    applyPatch();
    assertNotNull(root.findChild("f.txt"));
  }

  public void testPatchBetweenTwoOldRevisions() throws Exception {
    root.createChildData(null, "f1.txt");
    root.createChildData(null, "f2.txt");
    root.createChildData(null, "f3.txt");

    createPatchBetweenRevisions(3, 1);
    clearRoot();
    applyPatch();

    assertNotNull(root.findChild("f1.txt"));
    assertNotNull(root.findChild("f2.txt"));
    assertNull(root.findChild("f3.txt"));
  }

  public void testRename() throws Exception {
    VirtualFile f = root.createChildData(null, "f.txt");
    f.setBinaryContent(new byte[]{1});

    f.rename(null, "ff.txt");

    createPatchBetweenRevisions(1, 0);
    f.rename(null, "f.txt");
    applyPatch();

    VirtualFile patched = root.findChild("ff.txt");
    assertNull(root.findChild("f.txt"));
    assertNotNull(patched);
    assertEquals(1, patched.contentsToByteArray()[0]);
  }

  public void testReversePatch() throws Exception {
    root.createChildData(null, "f.txt");

    createPatchBetweenRevisions(1, 0, true);
    applyPatch();

    assertNull(root.findChild("f.txt"));
  }

  public void testDirectoryCreationWithFiles() throws Exception {
    VirtualFile dir = root.createChildDirectory(null, "dir");
    dir.createChildData(null, "f.txt");

    createPatchBetweenRevisions(2, 0, false);
    clearRoot();

    applyPatch();

    assertNotNull(root.findChild("dir"));
    assertNotNull(root.findChild("dir").findChild("f.txt"));
  }

  public void testDirectoryDeletionWithFiles() throws Exception {
    VirtualFile dir = root.createChildDirectory(null, "dir");
    dir.createChildData(null, "f1.txt");
    dir.createChildData(null, "f2.txt");

    dir.delete(null);
    createPatchBetweenRevisions(1, 0, false);

    dir = root.createChildDirectory(null, "dir");
    dir.createChildData(null, "f1.txt");
    dir.createChildData(null, "f2.txt");

    applyPatch();

    assertNotNull(root.findChild("dir"));
    assertNull(root.findChild("dir").findChild("f1.txt"));
    assertNull(root.findChild("dir").findChild("f2.txt"));
  }

  public void testDirectoryRename() throws Exception {
    VirtualFile dir = root.createChildDirectory(null, "dir1");
    dir.createChildData(null, "f.txt");

    dir.rename(null, "dir2");

    createPatchBetweenRevisions(1, 0);

    dir.rename(null, "dir1");

    applyPatch();

    VirtualFile afterDir1 = root.findChild("dir1");
    VirtualFile afterDir2 = root.findChild("dir2");
    assertNotNull(afterDir1);
    assertNotNull(afterDir2);

    assertNull(afterDir1.findChild("f.txt"));
    assertNotNull(afterDir2.findChild("f.txt"));
  }

  private void createPatchBetweenRevisions(int left, int right) throws Exception {
    createPatchBetweenRevisions(left, right, false);
  }

  private void createPatchBetweenRevisions(int left, int right, boolean reverse) throws Exception {
    List<Revision> rr = getVcsRevisionsFor(root);
    Revision l = rr.get(left);
    Revision r = rr.get(right);

    List<Difference> dd = l.getDifferencesWith(r);
    List<Change> cc = new ArrayList<Change>();
    for (Difference d : dd) {
      Change c = new Change(d.getLeftContentRevision(gateway), d.getRightContentRevision(gateway));
      cc.add(c);
    }

    PatchCreator.create(gateway, cc, patchFilePath, reverse);
  }
}
