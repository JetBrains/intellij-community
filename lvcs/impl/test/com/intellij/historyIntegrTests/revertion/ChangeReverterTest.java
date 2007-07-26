package com.intellij.historyIntegrTests.revertion;

import com.intellij.history.Clock;
import com.intellij.history.core.changes.Change;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.integration.revertion.ChangeReverter;
import com.intellij.history.utils.RunnableAdapter;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.Date;

public class ChangeReverterTest extends ChangeReverterTestCase {
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

  public void testRevertDeletionOfContentRootWithFiles() throws Exception {
    VirtualFile newRoot = addContentRootWithFiles(myModule, "f.java");
    String path = newRoot.getPath();

    newRoot.delete(null);

    revertLastChangeSet();

    VirtualFile restoredRoot = findFile(path);
    assertNotNull(restoredRoot);

    VirtualFile restoredFile = restoredRoot.findChild("f.java");
    assertNotNull(restoredFile);

    // should keep history, but due to order of events, in which RootsChanged
    // event arrives before FileCreated event, i could not think out easy way
    // to make it work so far.
    assertEquals(1, getVcsRevisionsFor(restoredRoot).size());
    assertEquals(1, getVcsRevisionsFor(restoredFile).size());
  }

  private void revertLastChangeSet() throws IOException {
    Change cs = getVcs().getChangeList().getChanges().get(0);
    ChangeReverter r = createReverter(cs);
    r.revert();
  }

  private VirtualFile findFile(String path) {
    return LocalFileSystem.getInstance().findFileByPath(path);
  }

  public void testRevertDeletion() throws Exception {
    VirtualFile f = root.createChildData(null, "f.java");
    f.delete(null);

    revertLastChange(root);

    f = root.findChild("f.java");
    assertNotNull(f);
    assertEquals(2, getVcsRevisionsFor(f).size());
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

  public void testRevertFullChangeSet() throws IOException {
    getVcs().beginChangeSet();
    VirtualFile f1 = root.createChildData(null, "f1.java");
    root.createChildData(null, "f2.java");
    getVcs().endChangeSet(null);

    revertLastChange(f1);

    assertNull(root.findChild("f1.java"));
    assertNull(root.findChild("f2.java"));
  }

  public void testRevertFullSubsequentChangeSet() throws IOException {
    VirtualFile dir = root.createChildDirectory(null, "dir");
    VirtualFile f1 = root.createChildData(null, "f1.java");

    getVcs().beginChangeSet();
    f1.move(null, dir);
    dir.createChildData(null, "f2.java");
    getVcs().endChangeSet(null);

    revertChange(f1, 1);

    assertNotNull(root.findChild("dir"));
    assertNull(root.findChild("f1.java"));
    assertNull(dir.findChild("f1.java"));
    assertNull(dir.findChild("f2.java"));
  }

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
    getVcs().putUserLabel("abc");
    f.rename(null, "ff.java");

    revertChange(f, 1);

    assertEquals(f, root.findChild("f.java"));
    assertNull(root.findChild("ff.java"));
  }

  public void testChangeSetNameAfterRevert() throws IOException {
    getVcs().beginChangeSet();
    VirtualFile f = root.createChildData(null, "f.java");
    getVcs().endChangeSet("file changed");

    revertLastChange(f);

    Revision r = getVcsRevisionsFor(root).get(0);
    assertEquals("Revert of 'file changed'", r.getCauseChangeName());
  }

  public void testChangeSetNameAfterRevertUnnamedChange() throws IOException {
    Clock.setCurrentTimestamp(new Date(2003, 00, 01, 12, 30).getTime());
    getVcs().beginChangeSet();
    VirtualFile f = root.createChildData(null, "f.java");
    getVcs().endChangeSet(null);

    revertLastChange(f);

    Revision r = getVcsRevisionsFor(root).get(0);
    assertEquals("Revert of change made 01.01.03 12:30", r.getCauseChangeName());
  }
}
