package com.intellij.historyIntegrTests.revertion;

import com.intellij.history.integration.revertion.Reverter;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class ChangeReverterCanRevertTest extends ChangeReverterTestCase {
  public void testDoesNotAskIfThereIsNoSubsequentalChanges() throws IOException {
    root.createChildData(null, "f.txt");

    Reverter r = createReverter(root, 0);
    assertTrue(r.askUserForProceeding().isEmpty());
  }

  public void testAskingForRevertSubsequentalChanges() throws IOException {
    VirtualFile f = root.createChildData(null, "f.txt");
    f.setBinaryContent(new byte[1]);

    Reverter r = createReverter(f, 1);
    List<String> questions = r.askUserForProceeding();
    
    String expected = "There are some changes that have been done after this one.\nThese changes should be reverted too.";
    assertEquals(Collections.singletonList(expected), questions);
  }

  public void testCanNotRevertRenameIfSomeFilesAlreadyExist() throws IOException {
    VirtualFile f = root.createChildData(null, "f.txt");

    f.rename(null, "ff.txt");
    assertCanRevert(f, 0);

    root.createChildData(null, "f.txt");
    assertCanNotRevert(f, 0, "some files already exist");
  }

  public void testCanNotRevertMovementIfSomeFilesAlreadyExist() throws IOException {
    VirtualFile f = root.createChildData(null, "f.txt");
    VirtualFile dir = root.createChildDirectory(null, "dir");

    f.move(null, dir);
    assertCanRevert(f, 0);

    root.createChildData(null, "f.txt");
    assertCanNotRevert(f, 0, "some files already exist");
  }

  public void testIncludeOnlyOnlyErrorMessage() throws IOException {
    VirtualFile f1 = root.createChildData(null, "f1.txt");
    VirtualFile f2 = root.createChildData(null, "f2.txt");
    VirtualFile dir = root.createChildDirectory(null, "dir");

    f1.move(null, dir);
    f2.move(null, dir);
    assertCanRevert(dir, 1);

    root.createChildData(null, "f1.txt");
    root.createChildData(null, "f2.txt");

    List<String> ee = getCanRevertErrors(dir, 1);
    assertEquals(1, ee.size());
    assertEquals("some files already exist", ee.get(0));
  }

  public void testDoesNotConsiderUnaffectedFiles() throws IOException {
    VirtualFile f1 = root.createChildData(null, "f1.txt");
    VirtualFile f2 = root.createChildData(null, "f2.txt");

    f1.rename(null, "f11.txt");
    f2.rename(null, "f22.txt");
    root.createChildData(null, "f2.txt");

    assertCanRevert(f1, 0);
  }

  public void testDoesNotConsiderPreviousChanges() throws IOException {
    VirtualFile f = root.createChildData(null, "f.txt");

    f.rename(null, "ff.txt");
    f.rename(null, "fff.txt");
    root.createChildData(null, "f.txt");

    assertCanRevert(f, 0);
  }
}