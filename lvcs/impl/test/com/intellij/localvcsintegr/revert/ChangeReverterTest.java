package com.intellij.localvcsintegr.revert;

import com.intellij.idea.Bombed;
import com.intellij.localvcs.core.revisions.Revision;
import com.intellij.localvcs.integration.Clock;
import com.intellij.localvcs.integration.revert.ChangeReverter;
import com.intellij.localvcs.utils.RunnableAdapter;
import com.intellij.localvcsintegr.IntegrationTestCase;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

@Bombed(month = Calendar.MAY, day = 21)
public class ChangeReverterTest extends IntegrationTestCase {
  public void testRevertCreation() throws IOException {
    VirtualFile f = root.createChildData(null, "f.java");

    revertLastChange(f);
    assertNull(root.findChild("f.java"));
  }

  public void testRevertChangeSetWithSeveralChanges() throws IOException {
    final VirtualFile[] ff = new VirtualFile[2];
    CommandProcessor.getInstance().executeCommand(myProject, new RunnableAdapter() {
      @Override
      public void doRun() throws IOException {
        ff[0] = root.createChildData(null, "f1.java");
        ff[1] = root.createChildData(null, "f2.java");
      }
    }, "", null);

    revertLastChange(ff[0]);
    assertNull(root.findChild("f1.java"));
    assertNull(root.findChild("f2.java"));
  }

  public void testDoesNotRevertAnotherChanges() throws IOException {
    VirtualFile f1 = root.createChildData(null, "f1.java");
    root.createChildData(null, "f2.java");

    revertLastChange(f1);

    assertNull(root.findChild("f1.java"));
    assertNotNull(root.findChild("f2.java"));
  }

  public void testRevertSubsequentChanges() throws IOException {
    VirtualFile f = root.createChildData(null, "f.java");
    f.setBinaryContent(new byte[]{1}, -1, 123);
    f.rename(null, "ff.java");
    f.setBinaryContent(new byte[]{2}, -1, 456);

    revertChange(f, 1); // rename

    assertEquals("f.java", f.getName());
    assertEquals(1, f.contentsToByteArray()[0]);
  }

  public void testRevertSubsequentMovements() throws IOException {
    VirtualFile f = root.createChildData(null, "f.java");
    VirtualFile dir = root.createChildDirectory(null, "dir");

    f.setBinaryContent(new byte[]{1}, -1, 123);
    f.move(null, dir);

    revertChange(f, 1); // content change

    assertEquals(root, f.getParent());
    assertEquals(0, f.contentsToByteArray().length);
    assertEquals(dir, root.findChild("dir"));
  }

  public void testRevertSubsequentParentMovement() throws IOException {
    VirtualFile dir1 = root.createChildDirectory(null, "dir1");
    VirtualFile dir2 = root.createChildDirectory(null, "dir2");
    VirtualFile f = dir1.createChildData(null, "f.java");

    f.setBinaryContent(new byte[]{1}, -1, 123);
    dir1.move(null, dir2);

    revertChange(f, 1); // content change

    assertEquals(0, f.contentsToByteArray().length);
    assertEquals(root, dir1.getParent());
    assertEquals(0, dir2.getChildren().length);
  }

  public void testRevertCreationOfParentInWhichFileWasMoved() throws IOException {
    VirtualFile dir1 = root.createChildDirectory(null, "dir1");

    VirtualFile f = dir1.createChildData(null, "f.java");
    f.setBinaryContent(new byte[]{1}, -1, 123);

    VirtualFile dir2 = root.createChildDirectory(null, "dir2");
    dir1.move(null, dir2);

    revertChange(f, 1); // content change

    assertEquals(0, f.contentsToByteArray().length);
    assertEquals(root, dir1.getParent());
    assertNull(root.findChild("dir2"));
  }

  public void testRevertDeletion() throws Exception {
    VirtualFile f = root.createChildData(null, "f.java");
    f.delete(null);

    revertLastChange(root);

    f = root.findChild("f.java");
    assertNotNull(f);
    assertEquals(1, getVcsRevisionsFor(f).size());
  }

  public void testRevertMovementAfterDeletion() throws Exception {
    VirtualFile dir1 = root.createChildDirectory(null, "dir1");
    VirtualFile dir2 = root.createChildDirectory(null, "dir2");
    VirtualFile f = dir1.createChildData(null, "f.java");

    f.delete(null);
    dir1.move(null, dir2);

    revertChange(dir1, 1); // deletion

    assertEquals(root, dir1.getParent());
    assertNotNull(dir1.findChild("f.java"));
  }

  public void testRevertDeletionOnPreviousParentAfterMovement() throws Exception {
    VirtualFile dir1 = root.createChildDirectory(null, "dir1");
    VirtualFile dir2 = root.createChildDirectory(null, "dir2");
    VirtualFile f = dir1.createChildData(null, "f.java");

    f.move(null, dir2);
    dir1.delete(null);

    revertLastChange(f); // movement

    dir1 = root.findChild("dir1");
    assertNotNull(dir1);
    assertEquals(dir1, f.getParent());
  }

  public void testRestoringNecessaryDirectoriesDuringSubsequentMovementsRevert() throws Exception {
    VirtualFile dir1 = root.createChildDirectory(null, "dir1");
    VirtualFile dir2 = root.createChildDirectory(null, "dir2");
    VirtualFile dir3 = root.createChildDirectory(null, "dir3");
    VirtualFile f = dir2.createChildData(null, "f.java");

    dir1.move(null, dir3);
    f.move(null, dir1);
    dir2.delete(null);

    revertChange(dir1, 1); // movement
    // should revert dir deletion and file movement

    dir2 = root.findChild("dir2");
    assertNotNull(dir2);
    assertEquals(dir2, f.getParent());
    assertEquals(root, dir1.getParent());
  }

  public void testRevertSubsequentalFileMovementFromDir() throws IOException {
    VirtualFile dir1 = root.createChildDirectory(null, "dir1");
    VirtualFile dir2 = root.createChildDirectory(null, "dir2");
    VirtualFile f = dir1.createChildData(null, "f.java");

    dir1.move(null, dir2);
    f.move(null, dir2);

    revertChange(dir1, 1); // movement

    assertEquals(root, dir1.getParent());
    assertEquals(dir1, f.getParent());
  }

  public void testRevertSeveralSubsequentalFileMovementsFromDir() throws IOException {
    VirtualFile dir1 = root.createChildDirectory(null, "dir1");
    VirtualFile dir2 = root.createChildDirectory(null, "dir2");
    VirtualFile dir3 = root.createChildDirectory(null, "dir3");
    VirtualFile f = dir1.createChildData(null, "f.java");

    dir1.move(null, dir2);
    f.move(null, dir2);
    f.move(null, dir3);

    revertChange(dir1, 1); // movement

    assertEquals(root, dir1.getParent());
    assertEquals(dir1, f.getParent());
  }

  //public void testCanNotRevertRenameIfSomeFilesAlreadyExist() throws IOException {
  //  VirtualFile f1 = root.createChildData(null, "f1.java");
  //  VirtualFile f2 = root.createChildData(null, "f2.java");
  //
  //  f1.rename(null, "f11.java");
  //  f2.rename(null, "f1.java");
  //
  //  assertCanNotRevertLastChange(f1);
  //}
  //
  //public void testCanNotRevertDeletionIfSomeFilesAlreadyExist() throws Exception {
  //  VirtualFile f1 = root.createChildData(null, "f1.java");
  //  VirtualFile f2 = root.createChildData(null, "f2.java");
  //
  //  f1.delete(null);
  //  f2.rename(null, "f1.java");
  //
  //  assertCanNotRevertLastChange(f1);
  //}
  //
  //public void testCanNotRevertMovementIfSomeFilesAlreadyExist() throws Exception {
  //  VirtualFile f1 = root.createChildData(null, "f1.java");
  //  VirtualFile f2 = root.createChildData(null, "f2.java");
  //  VirtualFile dir = root.createChildDirectory(null, "dir");
  //
  //  f1.move(null, dir);
  //  f2.rename(null, "f1.java");
  //
  //  assertCanNotRevertLastChange(f1);
  //}
  //
  //// todo test ro status clearing
  //public void testClearingROStatus() throws Exception {
  //  fail();
  //}
  //
  //public void testClearingROStatusOnlyFromExistedFiles() throws Exception {
  //  fail();
  //}

  public void testDoesNotRevertPrecedingChanges() throws IOException {
    VirtualFile f = root.createChildData(null, "f.java");
    f.setBinaryContent(new byte[]{1}, -1, 123);
    f.setBinaryContent(new byte[]{2}, -1, 456);

    revertLastChange(f);
    assertEquals(f, root.findChild("f.java"));
    assertEquals(1, f.contentsToByteArray()[0]);
  }

  public void testRevertLabelChange() throws IOException {
    VirtualFile f = root.createChildData(null, "f.java");
    getVcs().putLabel("abc");
    f.rename(null, "ff.java");

    revertChange(f, 1);

    assertEquals(f, root.findChild("ff.java"));
    assertNull(root.findChild("f.java"));
  }

  public void testChangeSetNameAfterRevert() throws IOException {
    getVcs().beginChangeSet();
    VirtualFile f = root.createChildData(null, "f.java");
    getVcs().endChangeSet("file changed");

    revertLastChange(f);

    Revision r = getVcsRevisionsFor(root).get(0);
    assertEquals("Revert 'file changed'", r.getCauseChangeName());
  }

  public void testChangeSetNameAfterRevertUnnamedChange() throws IOException {
    Clock.setCurrentTimestamp(new Date(2003, 00, 01, 12, 30).getTime());
    getVcs().beginChangeSet();
    VirtualFile f = root.createChildData(null, "f.java");
    getVcs().endChangeSet(null);

    revertLastChange(f);

    Revision r = getVcsRevisionsFor(root).get(0);
    assertEquals("Revert change made 01.01.03 12:30", r.getCauseChangeName());
  }

  private void revertLastChange(VirtualFile f) throws IOException {
    revertChange(f, 0);
  }

  private void revertChange(VirtualFile f, int index) throws IOException {
    createReverter(f, index).revert();
  }

  private void assertCanNotRevertLastChange(VirtualFile f) throws IOException {
    assertFalse(createReverter(f, 0).checkCanRevert().isEmpty());
  }

  private ChangeReverter createReverter(VirtualFile f, int index) {
    List<Revision> rr = getVcsRevisionsFor(f);
    return new ChangeReverter(getVcs(), gateway, rr.get(index).getCauseChange());
  }
}
