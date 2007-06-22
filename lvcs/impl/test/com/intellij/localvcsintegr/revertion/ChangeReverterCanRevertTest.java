package com.intellij.localvcsintegr.revertion;

import com.intellij.history.integration.revertion.Reverter;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.List;

public class ChangeReverterCanRevertTest extends ChangeReverterTestCase {
  public void testDoesNotAskIfThereIsNoSubsequentalChanges() throws IOException {
    root.createChildData(null, "f.java");

    Reverter r = createReverter(root, 0);
    assertNull(r.askUserForProceed());
  }

  public void testAskingForRevertSubsequentalChanges() throws IOException {
    VirtualFile f = root.createChildData(null, "f.java");
    f.setBinaryContent(new byte[1]);

    Reverter r = createReverter(f, 1);
    String question = r.askUserForProceed();
    assertEquals("There are some changes that have been done after this one.\nThese changes should be reverted too.", question);
  }

  public void testCanNotRevertRenameIfSomeFilesAlreadyExist() throws IOException {
    VirtualFile f = root.createChildData(null, "f.java");

    f.rename(null, "ff.java");
    assertCanRevert(f, 0);

    root.createChildData(null, "f.java");
    assertCanNotRevert(f, 0, "some files already exist");
  }

  public void testCanNotRevertMovementIfSomeFilesAlreadyExist() throws IOException {
    VirtualFile f = root.createChildData(null, "f.java");
    VirtualFile dir = root.createChildDirectory(null, "dir");

    f.move(null, dir);
    assertCanRevert(f, 0);

    root.createChildData(null, "f.java");
    assertCanNotRevert(f, 0, "some files already exist");
  }

  public void testIncludeOnlyOnlyErrorMessage() throws IOException {
    VirtualFile f1 = root.createChildData(null, "f1.java");
    VirtualFile f2 = root.createChildData(null, "f2.java");
    VirtualFile dir = root.createChildDirectory(null, "dir");

    f1.move(null, dir);
    f2.move(null, dir);
    assertCanRevert(dir, 1);

    root.createChildData(null, "f1.java");
    root.createChildData(null, "f2.java");

    List<String> ee = getCanRevertErrors(dir, 1);
    assertEquals(1, ee.size());
    assertEquals("some files already exist", ee.get(0));
  }

  public void testDoesNotConsiderUnaffectedFiles() throws IOException {
    VirtualFile f1 = root.createChildData(null, "f1.java");
    VirtualFile f2 = root.createChildData(null, "f2.java");

    f1.rename(null, "f11.java");
    f2.rename(null, "f22.java");
    root.createChildData(null, "f2.java");

    assertCanRevert(f1, 0);
  }

  public void testDoesNotConsiderPreviousChanges() throws IOException {
    VirtualFile f = root.createChildData(null, "f.java");

    f.rename(null, "ff.java");
    f.rename(null, "fff.java");
    root.createChildData(null, "f.java");

    assertCanRevert(f, 0);
  }
}